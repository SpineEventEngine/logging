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

package io.spine.logging.jvm;

import com.google.common.annotations.VisibleForTesting;
import io.spine.logging.jvm.backend.Metadata;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

import static io.spine.logging.jvm.LogContext.Key.LOG_EVERY_N;

/**
 * Rate limiter to support {@code every(N)} capability.
 *
 * <p>Instances of this class are created for each unique {@link LogSiteKey} for
 * which rate limiting via the {@code LOG_EVERY_N} metadata key is required.
 * This class implements {@code RateLimitStatus} as a mechanism for resetting
 * the rate limiter state.
 *
 * <p>Instances of this class are thread-safe.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/CountingRateLimiter.java">
 *         Original Java code of Google Flogger</a> for historical context.
 */
final class CountingRateLimiter extends RateLimitStatus {

    private static final LogSiteMap<CountingRateLimiter> map =
            new LogSiteMap<>() {
                @Override
                protected CountingRateLimiter initialValue() {
                    return new CountingRateLimiter();
                }
            };

    /**
     * Returns the status of the rate limiter, or {@code null} if the {@code LOG_EVERY_N} metadata
     * was not present.
     *
     * <p>The rate limiter status is {@code DISALLOW} until the log count exceeds
     * the specified limit, and then the limiter switches to its pending state and
     * returns an `allow` status until it is reset.
     */
    @Nullable
    static RateLimitStatus check(Metadata metadata, LogSiteKey logSiteKey) {
        var rateLimitCount = metadata.findValue(LOG_EVERY_N);
        if (rateLimitCount == null) {
            // Without rate limiter specific metadata, this limiter has no effect.
            return null;
        }
        return map.get(logSiteKey, metadata)
                  .incrementAndCheckLogCount(rateLimitCount);
    }

    /**
     * By setting the initial value as Integer#MAX_VALUE we ensure that the first time rate limiting
     * is checked, the rate limit count (which is only an Integer) must be reached, placing the
     * limiter into its pending state immediately. If this is the only limiter used,
     * this corresponds to the first log statement always being emitted.
     */
    private final AtomicLong invocationCount = new AtomicLong(Integer.MAX_VALUE);

    @VisibleForTesting
    CountingRateLimiter() {
    }

    /**
     * Increments the invocation count and returns true if it reached the specified
     * rate limit count.
     *
     * <p>This is invoked during post-processing if a rate-limiting count was set via
     * {@link MiddlemanApi#every(int)}.
     */
    @VisibleForTesting
    RateLimitStatus incrementAndCheckLogCount(int rateLimitCount) {
        return invocationCount.incrementAndGet() >= rateLimitCount ? this : DISALLOW;
    }

    // Reset function called to move the limiter out of the "pending" state after a log occurs.
    @Override
    public void reset() {
        invocationCount.set(0);
    }
}
