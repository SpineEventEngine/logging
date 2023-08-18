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

import com.google.common.flogger.CountingRateLimiter.check
import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.RateLimitStatus.DISALLOW
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CountingRateLimiter` should")
internal class CountingRateLimiterSpec {

    companion object {
        private const val RATE_LIMIT = 3
    }

    @Test
    fun `return 'null' if the corresponding metadata key is not present`() {
        // Not supplying `LOG_EVERY_N` metadata key ignores rate limiting by returning null.
        val metadata = FakeMetadata()
        val logSite = FakeLogSite.unique()
        check(metadata, logSite).shouldBeNull()
    }

    @Test
    fun `rate limit`() {
        val metadata = FakeMetadata().add(Key.LOG_EVERY_N, RATE_LIMIT)
        val logSite = FakeLogSite.unique()
        repeat(100) { i ->
            val status = check(metadata, logSite)
            val skipCount = RateLimitStatus.checkStatus(status, logSite, metadata)
            val shouldLog = skipCount != -1
            shouldLog shouldBe (i % 3 == 0)
        }
    }

    @Test
    fun `distinct different log sites`() {
        val metadata = FakeMetadata().add(Key.LOG_EVERY_N, RATE_LIMIT)
        val fooLog = FakeLogSite.unique()
        val barLog = FakeLogSite.unique()
        val allowFoo = check(metadata, fooLog)
        val allowBar = check(metadata, barLog)

        allowFoo shouldNotBe allowBar
        check(metadata, fooLog) shouldBeSameInstanceAs allowFoo
        check(metadata, barLog) shouldBeSameInstanceAs allowBar

        // `foo` is reset, so it moves into its rate-limiting state, but `bar` stays pending.
        allowFoo!!.reset()
        check(metadata, fooLog) shouldBeSameInstanceAs DISALLOW
        check(metadata, barLog) shouldBeSameInstanceAs allowBar

        allowBar!!.reset()
        check(metadata, fooLog) shouldBeSameInstanceAs DISALLOW
        check(metadata, barLog) shouldBeSameInstanceAs DISALLOW
    }

    @Test
    fun `increment counter and check the limit`() {
        val limiter = CountingRateLimiter()
        val allowStatus = limiter.incrementAndCheckLogCount(RATE_LIMIT)

        allowStatus shouldNotBe DISALLOW
        limiter.incrementAndCheckLogCount(RATE_LIMIT) shouldBeSameInstanceAs allowStatus
        limiter.incrementAndCheckLogCount(RATE_LIMIT) shouldBeSameInstanceAs allowStatus
        limiter.incrementAndCheckLogCount(RATE_LIMIT) shouldBeSameInstanceAs allowStatus

        // After a reset, we should disallow 2 logs before re-entering the pending state.
        allowStatus.reset()
        limiter.incrementAndCheckLogCount(RATE_LIMIT) shouldBeSameInstanceAs DISALLOW
        limiter.incrementAndCheckLogCount(RATE_LIMIT) shouldBeSameInstanceAs DISALLOW
        limiter.incrementAndCheckLogCount(RATE_LIMIT) shouldBeSameInstanceAs allowStatus
    }
}
