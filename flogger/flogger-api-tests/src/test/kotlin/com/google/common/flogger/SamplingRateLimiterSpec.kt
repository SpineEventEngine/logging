/*
 * Copyright (C) 2023 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.flogger

import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.RateLimitStatus.DISALLOW
import com.google.common.flogger.RateLimitStatus.checkStatus
import com.google.common.flogger.SamplingRateLimiter.check
import com.google.common.flogger.backend.Metadata
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`SamplingRateLimiter` should")
internal class SamplingRateLimiterSpec {

    @Test
    fun `ignore an invalid rate limiter`() {
        val metadata: Metadata = FakeMetadata().add(Key.LOG_SAMPLE_EVERY_N, 0)
        check(metadata, FakeLogSite.unique()).shouldBeNull()
    }

    @Test
    fun `count pending invocations`() {
        val limiter = SamplingRateLimiter()

        // Initially we are not “pending”, so disallow logging for an “impossible” sample rate.
        limiter.pendingCount.get() shouldBe 0
        limiter.sampleOneIn(Int.MAX_VALUE) shouldBe DISALLOW

        repeat(100) {
            limiter.sampleOneIn(5)
        }

        // Statistically we should be pending at least once.
        val pendingCount = limiter.pendingCount.get()
        pendingCount shouldBeGreaterThan 0

        // Now we are pending, we allow logging even for an “impossible” sample rate.
        limiter.sampleOneIn(Int.MAX_VALUE) shouldNotBe DISALLOW
        limiter.reset()
        limiter.pendingCount.get() shouldBe pendingCount - 1
    }

    @Test
    fun `rate limit`() {
        // Chance is less than one-millionth of 1% that this will fail spuriously.
        var metadata: Metadata = FakeMetadata().add(Key.LOG_SAMPLE_EVERY_N, 2)
        countNSamples(1000, metadata) shouldBeInRange 400..600

        // Expected average is 20 logs out of 1000. Seeing 0 or > 100 is enormously unlikely.
        metadata = FakeMetadata().add(Key.LOG_SAMPLE_EVERY_N, 50)
        countNSamples(1000, metadata) shouldBeInRange 1..100
    }
}

@Suppress("SameParameterValue") // For better readability.
private fun countNSamples(n: Int, metadata: Metadata): Int {
    val logSite = FakeLogSite.unique()
    var sampled = 0

    repeat(n) {
        val status = check(metadata, logSite)
        val skipped = checkStatus(status, logSite, metadata)
        if (skipped >= 0) {
            sampled++
        }
    }

    return sampled
}
