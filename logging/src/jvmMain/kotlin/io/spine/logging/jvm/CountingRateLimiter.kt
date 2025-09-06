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

package io.spine.logging.jvm

import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.LogContext.Key.LOG_EVERY_N
import io.spine.logging.LogSiteKey
import io.spine.logging.backend.Metadata
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate limiter to support `every(N)` capability.
 *
 * Instances of this class are created for each unique [io.spine.logging.LogSiteKey] for
 * which rate limiting via the [LOG_EVERY_N] metadata key is required.
 * This class implements [RateLimitStatus] as a mechanism for resetting
 * the rate limiter state.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/CountingRateLimiter.java">
 *       Original Java code of Google Flogger</a> for historical context.
 */
@ThreadSafe
internal class CountingRateLimiter : RateLimitStatus() {

    /**
     * By setting the initial value as [Int.MAX_VALUE] we ensure that the first time rate limiting
     * is checked, the rate limit count (which is only an Int) must be reached, placing the
     * limiter into its pending state immediately. If this is the only limiter used,
     * this corresponds to the first log statement always being emitted.
     */
    private val invocationCount = AtomicLong(Int.MAX_VALUE.toLong())

    /**
     * Increments the invocation count and returns true if it reached the specified
     * rate limit count.
     *
     * This is invoked during post-processing if a rate-limiting count was set via
     * [io.spine.logging.LoggingApi.every].
     */
    internal fun incrementAndCheckLogCount(rateLimitCount: Int): RateLimitStatus =
        if (invocationCount.incrementAndGet() >= rateLimitCount) {
            this
        } else {
            DISALLOW
        }

    /**
     * This function is called to move the limiter out of the "pending" state after a log occurs.
     */
    override fun reset() =
        invocationCount.set(0)

    companion object {

        private val map = object : LogSiteMap<CountingRateLimiter>() {
            override fun initialValue(): CountingRateLimiter = CountingRateLimiter()
        }

        /**
         * Returns the status of the rate limiter, or `null` if the `LOG_EVERY_N` metadata
         * was not present.
         *
         * The rate limiter status is [DISALLOW][RateLimitStatus.DISALLOW] until
         * the log count exceeds the specified limit, and then the limiter switches to
         * its pending state and returns an `allow` status until it is reset.
         */
        @JvmStatic
        fun check(metadata: Metadata, logSiteKey: LogSiteKey): RateLimitStatus? {
            val rateLimitCount = metadata.findValue(LOG_EVERY_N)
                // Without rate limiter specific metadata, this limiter has no effect.
                ?: return null
            return map[logSiteKey, metadata].incrementAndCheckLogCount(rateLimitCount)
        }
    }
}
