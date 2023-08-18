/*
 * Copyright (C) 2014 The Flogger Authors.
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

import com.google.common.flogger.DurationRateLimiter.check
import com.google.common.flogger.DurationRateLimiter.newRateLimitPeriod
import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.RateLimitStatus.DISALLOW
import com.google.common.flogger.RateLimitStatus.checkStatus
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DurationRateLimiter` should")
internal class DurationRateLimiterSpec {

    companion object {
        private val FAKE_TIMESTAMP = TimeUnit.SECONDS.toNanos(1234)
    }

    @Test
    fun `return 'null' if the corresponding metadata key is not present`() {
        // Not supplying the metadata key ignores rate limiting by returning null.
        check(FakeMetadata(), FakeLogSite.unique(), FAKE_TIMESTAMP).shouldBeNull()
    }

    @Test
    fun `rate limit`() {
        val oncePerSecond = newRateLimitPeriod(1, TimeUnit.SECONDS)
        val metadata = FakeMetadata().add(Key.LOG_AT_MOST_EVERY, oncePerSecond)
        val logSite = FakeLogSite.unique()
        repeat(100) { i ->
            // Increment by 1/10 of a second per log. We should then log once per 10 logs.
            val timestamp = FAKE_TIMESTAMP + i * MILLISECONDS.toNanos(100)
            val status = check(metadata, logSite, timestamp)
            val skipCount = checkStatus(status, logSite, metadata)
            val shouldLog = skipCount != -1
            shouldLog shouldBe (i % 10 == 0)
        }
    }

    @Test
    fun `distinct different log sites`() {
        val oncePerSecond = newRateLimitPeriod(1, TimeUnit.SECONDS)
        val metadata = FakeMetadata().add(Key.LOG_AT_MOST_EVERY, oncePerSecond)
        val fooLog = FakeLogSite.unique()
        val barLog = FakeLogSite.unique()

        var timestamp = FAKE_TIMESTAMP
        val allowFoo = check(metadata, fooLog, timestamp)
        val allowBar = check(metadata, barLog, timestamp)
        allowFoo shouldNotBe allowBar
        check(metadata, fooLog, timestamp) shouldBeSameInstanceAs allowFoo
        check(metadata, barLog, timestamp) shouldBeSameInstanceAs allowBar

        // `foo` is reset, so it moves into its rate-limiting state,
        // but `bar` stays pending.
        allowFoo!!.reset()
        timestamp += MILLISECONDS.toNanos(100)
        check(metadata, fooLog, timestamp) shouldBeSameInstanceAs DISALLOW
        check(metadata, barLog, timestamp) shouldBeSameInstanceAs allowBar

        // We reset `bar` after an additional 100ms has passed.
        // Both limiters are rate-limiting.
        allowBar!!.reset()
        timestamp += MILLISECONDS.toNanos(100)
        check(metadata, fooLog, timestamp) shouldBeSameInstanceAs DISALLOW
        check(metadata, barLog, timestamp) shouldBeSameInstanceAs DISALLOW

        // After 800ms, it has been 1 second since `foo` was reset,
        // but only 900ms since `bar` was reset, so `foo` becomes pending
        // and `bar` remains rate-limiting.
        timestamp += MILLISECONDS.toNanos(800)
        check(metadata, fooLog, timestamp) shouldBeSameInstanceAs allowFoo
        check(metadata, barLog, timestamp) shouldBeSameInstanceAs DISALLOW

        // After another 100ms, both limiters are pending again.
        timestamp += MILLISECONDS.toNanos(100)
        check(metadata, fooLog, timestamp) shouldBeSameInstanceAs allowFoo
        check(metadata, barLog, timestamp) shouldBeSameInstanceAs allowBar
    }

    @Test
    fun `check the limit in accordance to the given timestamp`() {
        val limiter = DurationRateLimiter()
        val period = newRateLimitPeriod(1, TimeUnit.SECONDS)

        // Arbitrary start time, but within the first period to ensure
        // we still log the first call.
        var timestamp = FAKE_TIMESTAMP

        // Always log for the first call, but not again in the same period.
        val allowStatus = limiter.checkLastTimestamp(timestamp, period)
        allowStatus shouldNotBe DISALLOW
        // Within the rate limit period we still return “allow” (because we have not been reset).
        timestamp += MILLISECONDS.toNanos(500)
        limiter.checkLastTimestamp(timestamp, period) shouldBeSameInstanceAs allowStatus
        // This sets the new log time to the last seen timestamp.
        allowStatus.reset()
        // Within 1 SECONDS, we disallow logging.
        timestamp += MILLISECONDS.toNanos(500)
        limiter.checkLastTimestamp(timestamp, period) shouldBeSameInstanceAs DISALLOW
        timestamp += MILLISECONDS.toNanos(499)
        limiter.checkLastTimestamp(timestamp, period) shouldBeSameInstanceAs DISALLOW
        // And at exactly 1 SECOND later, we allow logging again.
        timestamp += MILLISECONDS.toNanos(1)
        limiter.checkLastTimestamp(timestamp, period) shouldBeSameInstanceAs allowStatus
    }

    @Test
    fun `transform rate limit period to string`() {
        val period = newRateLimitPeriod(23, TimeUnit.SECONDS)
        "$period" shouldBe "23 SECONDS"
    }
}
