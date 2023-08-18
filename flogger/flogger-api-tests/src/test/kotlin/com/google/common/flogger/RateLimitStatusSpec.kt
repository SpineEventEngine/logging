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

import com.google.common.flogger.RateLimitStatus.ALLOW
import com.google.common.flogger.RateLimitStatus.DISALLOW
import com.google.common.flogger.RateLimitStatus.checkStatus
import com.google.common.flogger.RateLimitStatus.combine
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RateLimitStatusSpec` should")
internal class RateLimitStatusSpec {

    @Test
    fun `combine with constant statuses`() {
        val fooStatus = TestStatus()
        combine(null, null).shouldBeNull()

        // `null` is returned by rate-limiters when the rate limiter is not used,
        // and it is important to distinguish that from an explicit “allow”.
        combine(null, fooStatus) shouldBeSameInstanceAs fooStatus
        combine(fooStatus, null) shouldBeSameInstanceAs fooStatus

        // Not returning the “stateless” ALLOW is useful, but not strictly required.
        combine(ALLOW, fooStatus) shouldBeSameInstanceAs fooStatus
        combine(fooStatus, ALLOW) shouldBeSameInstanceAs fooStatus

        // Having “DISALLOW” be returned whenever present is essential for correctness.
        combine(DISALLOW, fooStatus) shouldBeSameInstanceAs DISALLOW
        combine(fooStatus, DISALLOW) shouldBeSameInstanceAs DISALLOW
    }

    @Test
    fun `combine with multiple statuses`() {
        val fooStatus = TestStatus()
        val barStatus = TestStatus()

        fooStatus.wasReset.shouldBeFalse()
        barStatus.wasReset.shouldBeFalse()

        val fooBarStatus = combine(fooStatus, barStatus)
        fooBarStatus!!.reset()

        fooStatus.wasReset.shouldBeTrue()
        barStatus.wasReset.shouldBeTrue()
    }

    @Test
    fun `use 'reset()' method of both statutes when combined`() {
        val fooStatus = TestStatus()
        val erroneousStatus: RateLimitStatus = object : RateLimitStatus() {
            public override fun reset() {
                throw IllegalStateException("badness")
            }
        }

        fooStatus.wasReset = false // For sure.
        var combinedStatus = combine(fooStatus, erroneousStatus)
        shouldThrow<IllegalStateException> {
            combinedStatus!!.reset()
        }
        fooStatus.wasReset.shouldBeTrue()

        // Same as above but just combining them in the opposite order.
        fooStatus.wasReset = false
        combinedStatus = combine(erroneousStatus, fooStatus)
        shouldThrow<IllegalStateException> {
            combinedStatus!!.reset()
        }
        fooStatus.wasReset.shouldBeTrue()
    }

    @Test
    fun `check allowed status`() {
        val metadata = FakeMetadata()
        // Use a different log site for each case to ensure the skip count is zero.

        // We wouldn't expect ALLOW to become the final status due to
        // how `combine()` works, but we can still test it.
        checkStatus(ALLOW, FakeLogSite.unique(), metadata) shouldBe 0

        // Any (status != DISALLOW) will be reset as part of this call.
        val fooStatus = TestStatus()
        checkStatus(fooStatus, FakeLogSite.unique(), metadata) shouldBe 0
        fooStatus.wasReset.shouldBeTrue()
    }

    @Test
    fun `check disallowed status`() {
        val metadata = FakeMetadata()
        // Having DISALLOW is the most common case for rate-limited log statements.
        checkStatus(DISALLOW, FakeLogSite.unique(), metadata) shouldBe -1
    }

    @Test
    fun `increment number of skipped invocations`() {
        val metadata = FakeMetadata()
        val logSite = FakeLogSite.unique()
        checkStatus(ALLOW, logSite, metadata) shouldBe 0
        checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        checkStatus(ALLOW, logSite, metadata) shouldBe 4
    }
}

private class TestStatus(private val shouldThrow: Boolean = false) : RateLimitStatus() {
    var wasReset = false
    public override fun reset() {
        if (shouldThrow) {
            throw IllegalStateException()
        }
        wasReset = true
    }
}
