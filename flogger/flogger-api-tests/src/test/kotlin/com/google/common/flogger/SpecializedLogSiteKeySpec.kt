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

package com.google.common.flogger

import com.google.common.flogger.testing.FakeLogSite.create
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [SpecializedLogSiteKey].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/SpecializedLogSiteKeyTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`SpecializedLogSiteKey` should")
internal class SpecializedLogSiteKeySpec {

    @Test
    fun `provide equality check`() {
        val logSite = create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = SpecializedLogSiteKey.of(logSite, "foo")

        val anotherFooKey = SpecializedLogSiteKey.of(logSite, "foo")
        anotherFooKey shouldBe fooKey
        anotherFooKey.hashCode() shouldBe fooKey.hashCode()

        val barKey = SpecializedLogSiteKey.of(logSite, "bar")
        barKey shouldNotBe fooKey
        barKey.hashCode() shouldNotBe fooKey.hashCode()

        val anotherLogSite = create("com.google.foo.Bar", "doOther", 23, "<unused>")
        val oneMoreFooKey = SpecializedLogSiteKey.of(anotherLogSite, "foo")
        oneMoreFooKey shouldNotBe fooKey
        oneMoreFooKey.hashCode() shouldNotBe fooKey.hashCode()
    }

    /**
     * Tests whether initialization order matters on equality check.
     *
     * Conceptually order does not matter, but it is hard to make equals
     * work efficiently and be order invariant. However, having two or more
     * specializations on a key will almost never happen, and even if it does,
     * the metadata preserves order at the log site, so keys should be
     * the same each time.
     *
     * Consider making equality invariant to specialization order if it can be done efficiently.
     */
    @Test
    fun `provide equality check with respect to delegation order`() {
        val logSite = create("com.google.foo.Foo", "doFoo", 42, "<unused>")

        val fooKey = SpecializedLogSiteKey.of(logSite, "foo")
        val barKey = SpecializedLogSiteKey.of(logSite, "bar")

        val fooBarKey = SpecializedLogSiteKey.of(fooKey, "bar")
        val barFooKey = SpecializedLogSiteKey.of(barKey, "foo")

        fooBarKey shouldNotBe barFooKey
    }
}
