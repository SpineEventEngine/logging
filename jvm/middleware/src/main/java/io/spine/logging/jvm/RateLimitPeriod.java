/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import io.spine.logging.jvm.backend.LogData;

import java.util.concurrent.TimeUnit;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * Immutable metadata for rate limiting based on a fixed count.
 *
 * <p>This corresponds to the {@code LOG_AT_MOST_EVERY} metadata key in {@link LogData}.
 *
 * <p>>Unlike the metadata for {@code every(N)}, we need to use a wrapper
 * class here to preserve the time unit information.
 */
final class RateLimitPeriod {

    @SuppressWarnings("FieldNamingConvention")
    private final int n;
    private final TimeUnit unit;

    RateLimitPeriod(int n, TimeUnit unit) {
        // This code will work with a zero-length time period, but it's nonsensical to try.
        if (n <= 0) {
            throw new IllegalArgumentException("time period must be positive: " + n);
        }
        this.n = n;
        this.unit = checkNotNull(unit, "time unit");
    }

    /**
     * Obtains the time of the {@code unit} repeated {@code n} times in nanoseconds.
     *
     * @implNote Since nanoseconds are the smallest level of precision a {@link TimeUnit}
     *   can express, we are guaranteed that "unit.toNanos(n) >= n > 0".
     *   This is important for correctness (see comment in checkLastTimestamp()) because
     *   it ensures the new timestamp that indicates when logging should occur always
     *   differs from the previous timestamp.
     */
    long toNanos() {
        return unit.toNanos(n);
    }

    @Override
    public String toString() {
        return n + " " + unit;
    }

    @Override
    public int hashCode() {
        // Rough and ready. We don't expect this be be needed much at all.
        return (n * 37) ^ unit.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RateLimitPeriod that) {
            return this.n == that.n && this.unit == that.unit;
        }
        return false;
    }
}
