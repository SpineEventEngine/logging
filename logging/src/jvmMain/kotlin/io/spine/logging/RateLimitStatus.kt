/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.logging

import io.spine.logging.backend.Metadata
import kotlinx.atomicfu.atomic

/**
 * Status for rate-limiting operations, usable by rate limiters and available to
 * subclasses of [LogContext] to handle rate limiting consistently.
 *
 * ## Design Notes
 *
 * The purpose of this class is to allow rate limiters to behave in a way which
 * is consistent when multiple rate limiters are combined for a single log statement.
 * If you are writing a rate limiter for logging which you want to "play well" with
 * other rate limiters, it is essential that you understand how `RateLimitStatus`
 * is designed to work.
 *
 * Firstly, [LogContext] tracks a single status for each log statement reached.
 * This is modified by code in the [postProcess()][LogContext.postProcess] method,
 * which can be overridden by custom logger implementations.
 *
 * When a rate limiter is used, it returns a `RateLimitStatus`, which is
 * combined with the existing value held in the context:
 *
 * ```kotlin
 * rateLimitStatus = RateLimitStatus.combine(rateLimitStatus, MyCustomRateLimiter.check(...))
 * ```
 *
 * A rate limiter should switch between two primary states "limiting" and "pending":
 *
 * - In the "limiting" state, the limiter should return the [DISALLOW] value
 *   and update any internal state until it reaches its trigger condition.
 *   Once the trigger condition is reached, the limiter enters the "pending" state.
 *
 * - In the "pending" state, the limiter returns an "allow" status _until it is [reset]_.
 *
 * This two-step approach means that, when multiple rate limiters are active
 * for a single log statement, logging occurs after all rate limiters are "pending"
 * (and at this point they are all reset). This is much more consistent than having
 * each rate limiter operate independently, and allows a much more intuitive
 * understanding of expected behaviour.
 *
 * It is recommended that most rate limiters should start in the "pending"
 * state to ensure that the first log statement they process is emitted
 * (even when multiple rate limiters are used).
 * This isn't required, but it should be documented either way.
 *
 * Each rate limiter is expected to follow this basic structure:
 *
 * ```kotlin
 * internal class CustomRateLimiter : RateLimitStatus() {
 *
 *     companion object {
 *         private val map = object : LogSiteMap<CustomRateLimiter>() {
 *             override fun initialValue(): CustomRateLimiter = CustomRateLimiter()
 *         }
 *
 *         fun check(metadata: Metadata, logSiteKey: LogSiteKey, ...): RateLimitStatus? {
 *             val rateLimitData = metadata.findValue(MY_CUSTOM_KEY) ?: return null
 *             return map[logSiteKey, metadata].checkRateLimit(rateLimitData, ...)
 *         }
 *     }
 *
 *     fun checkRateLimit(rateLimitData: MyRateLimitData, ...): RateLimitStatus {
 *         // Update internal state.
 *         return if (isPending) this else DISALLOW
 *     }
 *
 *     override fun reset() {
 *         // Reset from "pending" to "limiting" state
 *     }
 * }
 * ```
 *
 * The use of [LogSiteMap] ensures a rate limiter instance is held separately for each log
 * statement, but it also handles complex garbage collection issues around "specialized" log site
 * keys. All rate limiter implementations _must_ use this approach.
 *
 * Having the rate limiter class extend `RateLimitStatus` is a convenience for the case
 * where the [reset] operation requires no additional information.
 * If the [reset] operation requires extra state (e.g. from previous logging calls) then
 * this approach will not be possible, and a separate `RateLimitStatus` subclass would
 * need to be allocated to hold that state.
 *
 * Rate limiter instances _must_ be thread-safe, and should avoid using locks wherever
 * possible (since using explicit locking can cause unacceptable thread contention in highly
 * concurrent systems).
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/RateLimitStatus.java">
 *       Original Java code of Google Flogger</a> for historical context.
 */
public abstract class RateLimitStatus protected constructor() {

    /**
     * A log guard ensures that only one thread can claim "logging rights"
     * for a log statement once an "allow" rate limit status is set.
     *
     * It also tracks the number of skipped invocations of the log site key.
     *
     * Note that the skipped count is tracked via the "log site key" and there may be several
     * keys for a single log site (e.g., due to use of the `per(...)` methods).
     *
     * This is consistent with everywhere else which handles log site specific state,
     * but does make it a little less obvious what the skipped count refers to at first glance.
     */
    private class LogGuard {

        private val shouldReset = atomic(false)
        private val pendingLogCount = atomic(0)

        companion object {
            private val guardMap = object : LogSiteMap<LogGuard>() {
                override fun initialValue(): LogGuard = LogGuard()
            }

            fun checkAndGetSkippedCount(
                status: RateLimitStatus,
                logSiteKey: LogSiteKey,
                metadata: Metadata
            ): Int {
                val guard = guardMap[logSiteKey, metadata]
                // Pre-increment pendingCount to include this log statement, so (pendingCount > 0).
                val pendingCount = guard.pendingLogCount.incrementAndGet()
                if (status == DISALLOW || !guard.shouldReset.compareAndSet(false, true)) {
                    return -1
                }
                // Logging is allowed, and this thread has claimed the right to do it.
                try {
                    status.reset()
                } finally {
                    guard.shouldReset.value = false
                }
                // Subtract the pending count (this might not go to zero if other threads
                // are incrementing).
                guard.pendingLogCount.addAndGet(-pendingCount)
                // Return the skipped log count (which must be >= 0).
                return pendingCount - 1
            }
        }
    }

    /**
     * Resets an associated rate limiter, moving it out of the "pending" state and
     * back into rate limiting mode.
     *
     * Note: This method is never invoked concurrently with another `reset()` operation,
     * but it can be concurrent with calls to update rate limiter state.
     * Thus, it must be thread safe in general, but can assume it is the only reset operation
     * active for the limiter which returned it.
     */
    internal abstract fun reset()

    public companion object {

        /**
         * The status to return whenever a rate limiter determines that logging should not occur.
         *
         * All other statuses implicitly "allow" logging.
         */
        @JvmField
        public val DISALLOW: RateLimitStatus = sentinel()

        /**
         * The status to return whenever a stateless rate limiter determines that
         * logging should occur.
         *
         * Note: Truly stateless rate limiters should be _very_ rare, since they cannot hold
         * onto a pending "allow" state. Even a simple "sampling rate limiter" should be stateful
         * if once the "allow" state is reached it continues to be returned until logging actually
         * occurs.
         */
        @JvmField
        public val ALLOW: RateLimitStatus = sentinel()

        private fun sentinel(): RateLimitStatus = object : RateLimitStatus() {
            override fun reset() {
                // No-op for sentinel values
            }
        }

        /**
         * The rules for combining statuses are (in order):
         *
         * - If either value is `null`, the other value is returned (possibly `null`).
         * - If either value is [ALLOW] (the constant), the other non-null value is returned.
         * - If either value is [DISALLOW], [DISALLOW] is returned.
         * - Otherwise a combined status is returned from the two non-null "allow" statuses.
         *
         * In `LogContext` the `rateLimitStatus` field is set to the combined value of all
         * rate limiter statuses.
         *
         * This ensures that after rate limit processing:
         *
         * 1. If `rateLimitStatus == null` no rate limiters were applied, so logging is allowed.
         * 2. If `rateLimitStatus == DISALLOW`, the log was suppressed by rate limiting.
         * 3. Otherwise, the log statement was allowed, but rate limiters must now be reset.
         *
         * This code ensures that in the normal case of having no rate limiting for a log statement,
         * no allocations occur. It also ensures that (assuming well-written rate limiters) there
         * are no allocations for log statements using a single rate limiter.
         */
        @JvmStatic
        public fun combine(a: RateLimitStatus?, b: RateLimitStatus?): RateLimitStatus? =
            when {
                // In the vast majority of cases this code will be run once
                // per log statement, and at least one of 'a' or 'b' will be null.
                // So optimize early exiting for that case.
                a == null -> b
                b == null -> a
                // This is already a rare situation where 2 rate limiters are
                // active for the same log statement.
                // However, in most of these cases, at least one will likely "disallow" logging.
                a == DISALLOW || b == ALLOW -> a
                b == DISALLOW || a == ALLOW -> b
                // Getting here should be very rare and happens only when multiple
                // rate limiters have reached the "pending" state and logging should occur.
                // Neither status is `null`, `ALLOW` or `DISALLOW`.
                else -> object : RateLimitStatus() {
                    override fun reset() {
                        // Make sure both statuses are reset regardless of errors.
                        // If both throw errors, we only expose the 2nd one
                        // (we don't track "suppressed" exceptions).
                        // This is fine though since a `reset()` method should never risk
                        // throwing anything in the first place.
                        try {
                            a.reset()
                        } finally {
                            b.reset()
                        }
                    }
                }
            }

        /**
         * Checks rate limiter status and returns either the number of skipped log statements for
         * the `logSiteKey` (indicating that this log statement should be emitted) or `-1` if it
         * should be skipped.
         */
        @JvmStatic
        public fun checkStatus(
            status: RateLimitStatus,
            logSiteKey: LogSiteKey,
            metadata: Metadata
        ): Int = LogGuard.checkAndGetSkippedCount(status, logSiteKey, metadata)
    }
}
