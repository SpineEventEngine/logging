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

import io.spine.logging.jvm.LogContext.Key.LOG_SAMPLE_EVERY_N
import io.spine.logging.jvm.backend.Metadata
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rate limiter to support `onAverageEvery(N)` functionality.
 *
 * Instances of this class are created for each unique [LogSiteKey] for which rate limiting
 * via the `LOG_SAMPLE_EVERY_N` metadata key is required. This class implements
 * `RateLimitStatus` as a mechanism for resetting its own state.
 *
 * This class is thread safe.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/SamplingRateLimiter.java">
 *       Original Java code of Google Flogger</a> for historical context.
 */
internal class SamplingRateLimiter : RateLimitStatus() {

    internal val pendingCount = AtomicInteger()

    /**
     * Always "roll the dice" and adjust the count if necessary (even if we were already pending).
     * This means that in the long run we will account for every time we roll a zero, and the
     * number of logs will end up statistically close to 1-in-N (even if at times they can be
     * "bursty" due to the action of other rate limiting mechanisms).
     */
    internal fun sampleOneIn(rateLimitCount: Int): RateLimitStatus {
        val pending = if (random.get().nextInt(rateLimitCount) == 0) {
            pendingCount.incrementAndGet()
        } else {
            pendingCount.get()
        }
        return if (pending > 0) this else DISALLOW
    }

    override fun reset() {
        pendingCount.decrementAndGet()
    }

    companion object {

        private val map = object : LogSiteMap<SamplingRateLimiter>() {
            override fun initialValue(): SamplingRateLimiter = SamplingRateLimiter()
        }

        /**
         * Even though Random is synchronized, we have to put it in a ThreadLocal to avoid thread
         * contention. We cannot use ThreadLocalRandom (yet) due to JDK level.
         */
        private val random = ThreadLocal.withInitial { Random() }

        @JvmStatic
        fun check(metadata: Metadata, logSiteKey: LogSiteKey): RateLimitStatus? {
            val rateLimitCount = metadata.findValue(LOG_SAMPLE_EVERY_N)
            if (rateLimitCount == null || rateLimitCount <= 0) {
                // Without valid rate limiter specific metadata, this limiter has no effect.
                return null
            }
            return map[logSiteKey, metadata].sampleOneIn(rateLimitCount)
        }
    }
}
