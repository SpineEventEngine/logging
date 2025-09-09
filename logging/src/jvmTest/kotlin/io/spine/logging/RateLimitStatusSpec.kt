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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.RateLimitStatus
import io.spine.logging.RateLimitStatus.Companion.ALLOW
import io.spine.logging.RateLimitStatus.Companion.DISALLOW
import io.spine.logging.backend.given.FakeMetadata
import io.spine.logging.given.FakeLogSite
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [RateLimitStatus].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/RateLimitStatusTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`RateLimitStatusSpec` should")
internal class RateLimitStatusSpec {

    @Test
    fun `combine with constant statuses`() {
        val fooStatus = TestStatus()
        RateLimitStatus.combine(null, null).shouldBeNull()

        // `null` is returned by rate-limiters when the rate limiter is not used,
        // and it is important to distinguish that from an explicit “allow”.
        RateLimitStatus.combine(null, fooStatus) shouldBeSameInstanceAs fooStatus
        RateLimitStatus.combine(fooStatus, null) shouldBeSameInstanceAs fooStatus

        // Not returning the “stateless” ALLOW is useful, but not strictly required.
        RateLimitStatus.combine(ALLOW, fooStatus) shouldBeSameInstanceAs fooStatus
        RateLimitStatus.combine(fooStatus, ALLOW) shouldBeSameInstanceAs fooStatus

        // Having “DISALLOW” be returned whenever present is essential for correctness.
        RateLimitStatus.combine(DISALLOW, fooStatus) shouldBeSameInstanceAs DISALLOW
        RateLimitStatus.combine(fooStatus, DISALLOW) shouldBeSameInstanceAs DISALLOW
    }

    @Test
    fun `combine with multiple statuses`() {
        val fooStatus = TestStatus()
        val barStatus = TestStatus()

        fooStatus.wasReset.shouldBeFalse()
        barStatus.wasReset.shouldBeFalse()

        val fooBarStatus = RateLimitStatus.combine(fooStatus, barStatus)
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
        var combinedStatus = RateLimitStatus.combine(fooStatus, erroneousStatus)
        shouldThrow<IllegalStateException> {
            combinedStatus!!.reset()
        }
        fooStatus.wasReset.shouldBeTrue()

        // Same as above but just combining them in the opposite order.
        fooStatus.wasReset = false
        combinedStatus = RateLimitStatus.combine(erroneousStatus, fooStatus)
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
        RateLimitStatus.checkStatus(ALLOW, FakeLogSite.unique(), metadata) shouldBe 0

        // Any (status != DISALLOW) will be reset as part of this call.
        val fooStatus = TestStatus()
        RateLimitStatus.checkStatus(fooStatus, FakeLogSite.unique(), metadata) shouldBe 0
        fooStatus.wasReset.shouldBeTrue()
    }

    @Test
    fun `check disallowed status`() {
        val metadata = FakeMetadata()
        // Having DISALLOW is the most common case for rate-limited log statements.
        RateLimitStatus.checkStatus(DISALLOW, FakeLogSite.unique(), metadata) shouldBe -1
    }

    @Test
    fun `increment number of skipped invocations`() {
        val metadata = FakeMetadata()
        val logSite = FakeLogSite.unique()
        RateLimitStatus.checkStatus(ALLOW, logSite, metadata) shouldBe 0
        RateLimitStatus.checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        RateLimitStatus.checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        RateLimitStatus.checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        RateLimitStatus.checkStatus(DISALLOW, logSite, metadata) shouldBe -1
        RateLimitStatus.checkStatus(ALLOW, logSite, metadata) shouldBe 4
    }
}

private class TestStatus(private val shouldThrow: Boolean = false) : RateLimitStatus() {
    var wasReset = false
    override fun reset() {
        check(!shouldThrow)
        wasReset = true
    }
}
