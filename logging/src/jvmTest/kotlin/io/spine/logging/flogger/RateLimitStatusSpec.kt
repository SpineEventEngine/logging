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

import io.spine.logging.flogger.RateLimitStatus.ALLOW
import io.spine.logging.flogger.RateLimitStatus.DISALLOW
import io.spine.logging.flogger.RateLimitStatus.checkStatus
import io.spine.logging.flogger.RateLimitStatus.combine
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

/**
 * Tests for [RateLimitStatus].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/RateLimitStatusTest.java">
 *     Original Java code of Google Flogger</a>
 */
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
            public override fun reset() = error("badness")
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
        check(!shouldThrow)
        wasReset = true
    }
}
