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
import io.spine.logging.LogContext.Key.LOG_AT_MOST_EVERY
import io.spine.logging.LogSiteKey
import io.spine.logging.RateLimitStatus
import io.spine.logging.backend.Metadata
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlinx.atomicfu.atomic

/**
 * Rate limiter to support `atMostEvery(N, units)` functionality.
 *
 * Instances of this class are created for each unique [io.spine.logging.LogSiteKey] for which
 * rate limiting via the [LOG_AT_MOST_EVERY] metadata key is required.
 * This class implements `RateLimitStatus` as a mechanism for resetting the rate limiter state.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/DurationRateLimiter.java">
 *       Original Java code of Google Flogger</a> for historical context.
 */
@ThreadSafe
internal class DurationRateLimiter : RateLimitStatus() {

    private val lastTimestampNanos = atomic(-1L)

    /**
     * Checks whether the current time stamp is after the rate-limiting period and if so,
     * updates the time stamp and returns true.
     *
     * This is invoked during post-processing if a rate-limiting
     * duration was set via [io.spine.logging.LoggingApi.atMostEvery].
     */
    internal fun checkLastTimestamp(
        timestampNanos: Long,
        period: RateLimitPeriod
    ): RateLimitStatus {
        require(timestampNanos >= 0) { "Timestamp cannot be negative: $timestampNanos." }
        // If this is negative, we are in the pending state and will return "allow" until we are
        // reset. The value held here is updated to be the most recent negated timestamp, and is
        // negated again (making it positive and setting us into the rate-limiting state) when we
        // are reset.
        val lastNanos = lastTimestampNanos.value
        if (lastNanos >= 0) {
            val deadlineNanos = lastNanos + period.toNanos()
            // Check for negative deadline to avoid overflow for ridiculous durations. Assume
            // overflow always means "no logging".
            if (deadlineNanos !in 0..timestampNanos) {
                return DISALLOW
            }
        }
        // When logging is triggered, negate the timestamp to move us into the "pending" state and
        // return our reset status.
        // We don't want to race with the reset function (which may have already set a new
        // timestamp).
        lastTimestampNanos.compareAndSet(lastNanos, -timestampNanos)
        return this
    }

    /**
     * Reset function called to move the limiter out of the "pending" state.
     *
     * We do this by negating the timestamp (which was already negated when
     * we entered the pending state).
     *
     * This restores it to a positive value which moves us back into the "limiting" state.
     */
    override fun reset() {
        // Only one thread at a time can reset a rate limiter, so this can be unconditional. We
        // should only be able to get here if the timestamp was set to a negative value above.
        // However use max() to make sure we always move out of the pending state.
        lastTimestampNanos.value = max(-lastTimestampNanos.value, 0)
    }

    companion object {

        private val map = object : LogSiteMap<DurationRateLimiter>() {
            override fun initialValue(): DurationRateLimiter = DurationRateLimiter()
        }

        /**
         * Creates a period for rate limiting for the specified duration.
         *
         * This is invoked by the [LogContext.atMostEvery] method to create a metadata value.
         */
        @JvmStatic
        fun newRateLimitPeriod(n: Int, unit: DurationUnit): RateLimitPeriod =
            // We could cache commonly used values here if we wanted.
            RateLimitPeriod(n, unit)

        /**
         * Returns whether the log site should log based on the value of the [LOG_AT_MOST_EVERY]
         * metadata value and the current log site timestamp.
         */
        @JvmStatic
        fun check(
            metadata: Metadata,
            logSiteKey: LogSiteKey,
            timestampNanos: Long
        ): RateLimitStatus? {
            val rateLimitPeriod = metadata.findValue(LOG_AT_MOST_EVERY)
                // Without rate limiter specific metadata, this limiter has no effect.
                ?: return null
            return map[logSiteKey, metadata].checkLastTimestamp(timestampNanos, rateLimitPeriod)
        }
    }
}
