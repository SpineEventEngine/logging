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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.LogContext.Key
import io.spine.logging.RateLimitStatus
import io.spine.logging.backend.given.FakeMetadata
import io.spine.logging.RateLimitStatus.Companion.DISALLOW
import io.spine.logging.jvm.given.FakeLogSite
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.DurationUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [DurationRateLimiter].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/DurationRateLimiterTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`DurationRateLimiter` should")
internal class DurationRateLimiterSpec {

    companion object {
        private val FAKE_TIMESTAMP = TimeUnit.SECONDS.toNanos(1234)
    }

    @Test
    fun `return 'null' if the corresponding metadata key is not present`() {
        // Not supplying the metadata key ignores rate limiting by returning null.
        DurationRateLimiter.check(
            FakeMetadata(),
            FakeLogSite.unique(),
            FAKE_TIMESTAMP
        ).shouldBeNull()
    }

    @Test
    fun `rate limit`() {
        val oncePerSecond = DurationRateLimiter.newRateLimitPeriod(1, SECONDS)
        val metadata = FakeMetadata().add(Key.LOG_AT_MOST_EVERY, oncePerSecond)
        val logSite = FakeLogSite.unique()
        repeat(100) { i ->
            // Increment by 1/10 of a second per log. We should then log once per 10 logs.
            val timestamp = FAKE_TIMESTAMP + i * MILLISECONDS.toNanos(100)
            val status = DurationRateLimiter.check(metadata, logSite, timestamp)
            val skipCount = RateLimitStatus.checkStatus(status!!, logSite, metadata)
            val shouldLog = skipCount != -1
            shouldLog shouldBe (i % 10 == 0)
        }
    }

    @Test
    fun `distinct different log sites`() {
        val oncePerSecond = DurationRateLimiter.newRateLimitPeriod(1, SECONDS)
        val metadata = FakeMetadata().add(Key.LOG_AT_MOST_EVERY, oncePerSecond)
        val fooLog = FakeLogSite.unique()
        val barLog = FakeLogSite.unique()

        var timestamp = FAKE_TIMESTAMP
        val allowFoo = DurationRateLimiter.check(metadata, fooLog, timestamp)
        val allowBar = DurationRateLimiter.check(metadata, barLog, timestamp)
        allowFoo shouldNotBe allowBar
        DurationRateLimiter.check(metadata, fooLog, timestamp) shouldBeSameInstanceAs allowFoo
        DurationRateLimiter.check(metadata, barLog, timestamp) shouldBeSameInstanceAs allowBar

        // `foo` is reset, so it moves into its rate-limiting state,
        // but `bar` stays pending.
        allowFoo!!.reset()
        timestamp += MILLISECONDS.toNanos(100)
        DurationRateLimiter.check(metadata, fooLog, timestamp) shouldBeSameInstanceAs DISALLOW
        DurationRateLimiter.check(metadata, barLog, timestamp) shouldBeSameInstanceAs allowBar

        // We reset `bar` after an additional 100ms has passed.
        // Both limiters are rate-limiting.
        allowBar!!.reset()
        timestamp += MILLISECONDS.toNanos(100)
        DurationRateLimiter.check(metadata, fooLog, timestamp) shouldBeSameInstanceAs DISALLOW
        DurationRateLimiter.check(metadata, barLog, timestamp) shouldBeSameInstanceAs DISALLOW

        // After 800ms, it has been 1 second since `foo` was reset,
        // but only 900ms since `bar` was reset, so `foo` becomes pending
        // and `bar` remains rate-limiting.
        timestamp += MILLISECONDS.toNanos(800)
        DurationRateLimiter.check(metadata, fooLog, timestamp) shouldBeSameInstanceAs allowFoo
        DurationRateLimiter.check(metadata, barLog, timestamp) shouldBeSameInstanceAs DISALLOW

        // After another 100ms, both limiters are pending again.
        timestamp += MILLISECONDS.toNanos(100)
        DurationRateLimiter.check(metadata, fooLog, timestamp) shouldBeSameInstanceAs allowFoo
        DurationRateLimiter.check(metadata, barLog, timestamp) shouldBeSameInstanceAs allowBar
    }

    @Test
    fun `check the limit in accordance to the given timestamp`() {
        val limiter = DurationRateLimiter()
        val period = DurationRateLimiter.newRateLimitPeriod(1, SECONDS)

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
        val period = DurationRateLimiter.newRateLimitPeriod(23, SECONDS)
        "$period" shouldBe "23 SECONDS"
    }
}
