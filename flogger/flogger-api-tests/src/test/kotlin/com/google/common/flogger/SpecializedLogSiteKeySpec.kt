/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.testing.FakeLogSite.create
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
