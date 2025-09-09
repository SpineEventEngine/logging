/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.logging.MetadataKey.Companion.repeated
import io.spine.logging.MetadataKey.Companion.single
import io.spine.logging.backend.Platform
import io.spine.logging.given.iterate
import io.spine.logging.util.RecursionDepth
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [MetadataKey].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/MetadataKeyTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`MetadataKey` should")
internal class MetadataKeySpec {

    @Test
    fun `create a key for a single piece of metadata`() {
        val labels = mutableListOf("foo", "foo_bar", "FooBar")
        labels.forEach { label ->
            val key = single<String>(label)
            key.label shouldBe label
        }
    }

    @Test
    fun `fail on an invalid metadata identifier`() {
        val badLabels = mutableListOf("", "foo bar", "_FOO")
        badLabels.forEach { label ->
            shouldThrow<IllegalArgumentException> {
                MetadataKey.of(label, String::class, false)
            }
        }
    }

    @Test
    fun `cast an arbitrary value to the type of a key`() {
        val key = single<String>("foo")
        key.cast("value") shouldBe "value"
    }

    @Test
    fun `throw if can't cast value to the type of a key`() {
        val key = single<String>("foo")
        shouldThrow<ClassCastException> {
            key.cast(123)
        }
    }

    @Test
    fun `emit key-value pair for a single value`() {
        val key = single<String>("foo")
        val memoizingHandler = MemoizingKvHandler()
        key.safeEmit("123", memoizingHandler)
        memoizingHandler.entries.shouldHaveSize(1)
        memoizingHandler.entries shouldContain "foo=123"
    }

    @Test
    fun `emit key-value pairs for several values`() {
        val key = repeated<String>("foo")
        val memoizingHandler = MemoizingKvHandler()
        key.safeEmitRepeated(iterate("123", "abc"), memoizingHandler)
        memoizingHandler.entries.shouldHaveSize(2)
        memoizingHandler.entries shouldContainExactly listOf("foo=123", "foo=abc")
    }

    @Test
    fun `fail on attempt to emit several values for a singleton key`() {
        val key = single<String>("foo")
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
}

/**
 * A metadata key, which simulates a situation where a custom key accidentally
 * causes recursive logging with itself.
 *
 * Such is possible if the key is added to a context because all logging will now
 * include that key, even in code, which has no explicit knowledge of it.
 */
private class ReenteringKey(label: String) : MetadataKey<Any>(label, Any::class, true) {

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
