/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.logging.flogger

import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.flogger.CountingRateLimiter.check
import io.spine.logging.flogger.FloggerLogContext.Key
import io.spine.logging.flogger.RateLimitStatus.DISALLOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [CountingRateLimiter].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/CountingRateLimiterTest.java">
 *     Original Java code of Google Flogger</a>
 */
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
