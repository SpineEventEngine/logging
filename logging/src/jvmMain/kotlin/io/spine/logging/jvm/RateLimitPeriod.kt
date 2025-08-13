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

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.jvm.backend.LogData
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toTimeUnit

/**
 * Immutable metadata for rate limiting based on a fixed count.
 *
 * This corresponds to the
 * [LOG_AT_MOST_EVERY][io.spine.logging.jvm.LogContext.Key.LOG_AT_MOST_EVERY]
 * metadata key in [LogData].
 *
 * Unlike the metadata for `every(N)`, we need to use a wrapper class here to preserve
 * the time unit information for accurate rate limit calculations.
 *
 * @property n The number of time units that define the rate-limiting period. Must be positive.
 * @property unit The time unit used for the rate-limiting period (e.g., seconds, minutes).
 */
@Immutable
@ThreadSafe
public class RateLimitPeriod(
    private val n: Int,
    private val unit: DurationUnit
) {

    init {
        require(n > 0) { "A time period must be positive: $n." }
    }

    /**
     * Obtains the time of the [unit] repeated [n] times in nanoseconds.
     *
     * ## Implementation note
     *
     * Since nanoseconds are the smallest level of precision a [TimeUnit] can express,
     * we are guaranteed that `unit.toNanos(n) >= n > 0`. This is important for
     * correctness (see comment in `checkLastTimestamp()`) because it ensures the new
     * timestamp that indicates when logging should occur always differs from the
     * previous timestamp for proper rate limiting behavior.
     */
    internal fun toNanos(): Long = unit.toTimeUnit().toNanos(n.toLong())

    override fun toString(): String = "$n $unit"

    override fun hashCode(): Int {
        // Rough and ready. We don't expect this to be needed much at all.
        return (n * 37) xor unit.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RateLimitPeriod) return false
        return n == other.n && unit == other.unit
    }
}
