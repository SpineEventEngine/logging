/*
* Copyright (C) 2018 The Flogger Authors.
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

import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.backend.Platform
import com.google.common.flogger.given.MemoizingKvHandler
import com.google.common.flogger.given.iterate
import com.google.common.flogger.util.RecursionDepth
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`MetadataKey` should")
internal class MetadataKeySpec {

    @Test
    fun `create a key for a single piece of metadata`() {
        val labels = mutableListOf("foo", "foo_bar", "FooBar")
        labels.forEach { label ->
            val key = single(label, String::class.java)
            key.label shouldBe label
        }
    }

    @Test
    fun `fail on an invalid metadata identifier`() {
        val badLabels = mutableListOf("", "foo bar", "_FOO")
        badLabels.forEach { label ->
            shouldThrow<IllegalArgumentException> {
                MetadataKey(label, String::class.java, false)
            }
        }
    }

    @Test
    fun `cast an arbitrary value to the type of a key`() {
        val key = single("foo", String::class.java)
        key.cast("value") shouldBe "value"
    }

    @Test
    fun `throw if can't cast value to the type of a key`() {
        val key = single("foo", String::class.java)
        shouldThrow<ClassCastException> {
            key.cast(123)
        }
    }

    @Test
    fun `emit key-value pair for a single value`() {
        val key = single("foo", String::class.java)
        val memoizingHandler = MemoizingKvHandler()
        key.safeEmit("123", memoizingHandler)
        memoizingHandler.entries.shouldHaveSize(1)
        memoizingHandler.entries shouldContain "foo=123"
    }

    @Test
    fun `emit key-value pairs for several values`() {
        val key = repeated("foo", String::class.java)
        val memoizingHandler = MemoizingKvHandler()
        key.safeEmitRepeated(iterate("123", "abc"), memoizingHandler)
        memoizingHandler.entries.shouldHaveSize(2)
        memoizingHandler.entries shouldContainExactly listOf("foo=123", "foo=abc")
    }

    @Test
    fun `fail on attempt to emit several values for a singleton key`() {
        val key = single("foo", String::class.java)
        val memoizingHandler = MemoizingKvHandler()
        shouldThrow<IllegalStateException> {
            key.safeEmitRepeated(iterate("123", "abc"), memoizingHandler)
        }
    }

    @Test
    fun `prevent recursive key-value emission for a single value`() {
        val reentrant = ReenteringKey("reentrant")
        val handler = MemoizingKvHandler()
        reentrant.safeEmit("abc", handler)

        // Max recursion depth is 20 (see `MetadataKey.MAX_CUSTOM_METADATAKEY_RECURSION_DEPTH`),
        // but the initial log statement has no recursion, so 21 calls before
        // mitigation should occur.
        val expected = mutableListOf<String>()
        repeat(21) { i ->
            expected.add("depth-$i=<<abc>>")
        }

        // The non-customized key/value representation is emitted when recursion is halted.
        expected.add("reentrant=abc")

        handler.entries shouldContainExactly expected
    }

    @Test
    fun `prevent recursive key-value emission for several values`() {
        val reentrant = ReenteringKey("reentrant")
        val handler = MemoizingKvHandler()
        reentrant.safeEmitRepeated(iterate("foo", "bar"), handler)

        // Max recursion depth is 20 (see `MetadataKey.MAX_CUSTOM_METADATAKEY_RECURSION_DEPTH`),
        // but the initial log statement has no recursion, so 21 calls before
        // mitigation should occur.
        val expected = mutableListOf<String>()
        repeat(21) { i ->
            expected.add("depth-$i=[foo, bar]")
        }

        // The non-customized key/value representation is emitted when recursion is halted.
        // About repeated values, that's two entries due to how the fake handler is written.
        expected.add("reentrant=foo")
        expected.add("reentrant=bar")

        handler.entries shouldContainExactly expected
    }

    @Test
    fun `throw on 'null's`() {
        val badInstantiations = listOf(
            { MetadataKey(null, String::class.java, false) },
            { MetadataKey<Any>("label", null, false) },
            { single(null, String::class.java) },
            { single("label", null) },
            { repeated(null, String::class.java) },
            { repeated("label", null) }
        )
        badInstantiations.forEach { action ->
            shouldThrow<NullPointerException> { action() }
        }
    }
}

/**
 * A metadata key, which simulates a situation where a custom key accidentally
 * causes recursive logging with itself.
 *
 * Such is possible if the key is added to a context because all logging will now
 * include that key, even in code, which has no explicit knowledge of it.
 */
private class ReenteringKey(label: String) :
    MetadataKey<Any>(label, Any::class.java, true) {

    override fun emit(value: Any, kvh: KeyValueHandler) {
        val currentDepth = Platform.getCurrentRecursionDepth()
        kvh.handle("depth-$currentDepth", "<<$value>>") // Expected handling of value.
        RecursionDepth.enterLogStatement().use {
            safeEmit(value, kvh)
        }
    }

    override fun emitRepeated(values: Iterator<Any>, kvh: KeyValueHandler) {
        // Hack for test to preserve the given values past a single use.
        // In normal logging there would be a new `Metadata` instance created
        // for each reentrant logging call.
        val copy = values.asSequence().toList()
        val currentDepth = Platform.getCurrentRecursionDepth()
        kvh.handle("depth-$currentDepth", copy) // Expected handling of value.
        RecursionDepth.enterLogStatement().use {
            safeEmitRepeated(copy.iterator(), kvh)
        }
    }
}
