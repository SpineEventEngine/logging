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

package io.spine.logging.flogger.context

import io.spine.logging.flogger.context.SegmentTrie.create
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [SegmentTrie].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/context/SegmentTrieTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`SegmentTrie` should return")
internal class SegmentTrieSpec {

    companion object {
        private const val DEFAULT = "DEFAULT"
    }

    @Test
    fun `the default value when given an empty mapping`() {
        val map = emptyMap<String, String>()
        val trie = create(map, '.', DEFAULT)
        trie[""] shouldBe DEFAULT
        trie["."] shouldBe DEFAULT
        trie["foo"] shouldBe DEFAULT
    }

    @Nested inner class
    `the value that most closely matches the given key` {

        @Test
        fun `when given a singleton mapping`() {
            val map = mapOf("com.foo" to "FOO")
            val trie = create(map, '.', DEFAULT)

            trie["com.foo"] shouldBe "FOO"
            trie["com.foo.xxx"] shouldBe "FOO"

            trie[""] shouldBe DEFAULT
            trie["com"] shouldBe DEFAULT
            trie["com.foobar"] shouldBe DEFAULT
            trie["xxx"] shouldBe DEFAULT
        }

        @Test
        fun `when given a mapping with only a separating char`() {
            val map = mapOf("..." to "DOT")
            val trie = create(map, '.', DEFAULT)

            trie["..."] shouldBe "DOT"
            trie["...."] shouldBe "DOT"
            trie["....."] shouldBe "DOT"
            trie["....x"] shouldBe "DOT"

            trie[""] shouldBe DEFAULT
            trie["."] shouldBe DEFAULT
            trie[".."] shouldBe DEFAULT
            trie["x..."] shouldBe DEFAULT
            trie[".x.."] shouldBe DEFAULT
            trie["..x."] shouldBe DEFAULT
            trie["...x"] shouldBe DEFAULT
        }

        @Test
        fun `when given a mapping with only an empty key`() {
            val map = mapOf("" to "FOO")
            val trie = create(map, '.', DEFAULT)

            trie[""] shouldBe "FOO"
            trie["."] shouldBe "FOO"
            trie[".."] shouldBe "FOO"
            trie[".x"] shouldBe "FOO"

            trie["x"] shouldBe DEFAULT
            trie["x."] shouldBe DEFAULT
            trie["x.."] shouldBe DEFAULT
            trie["x.y"] shouldBe DEFAULT
        }

        @Test
        fun `when given a mapping with only a 'null' key`() {
            val map = mapOf<String?, String>(null to "BAD")
            assertThrows<NullPointerException> {
                create(map, '.', DEFAULT)
            }
        }

        @Test
        fun `when given a mapping with only a 'null' value`() {
            val map = mapOf<String, String?>("com.foo" to null)
            val trie = create(map, '.', DEFAULT)

            trie["com.foo"].shouldBeNull()
            trie["com.foo.xxx"].shouldBeNull()

            trie["com"] shouldBe DEFAULT
            trie["com.foobar"] shouldBe DEFAULT
            trie["xxx"] shouldBe DEFAULT
        }

        @Test
        fun `when given a general case mapping`() {
            val map = mapOf(
                "com.bar" to "BAR",
                "com.foo" to "FOO",
                "com.foo.bar" to "FOO_BAR",
                "com.quux" to "QUUX"
            )
            val trie = create(map, '.', DEFAULT)

            trie["com.bar"] shouldBe "BAR"
            trie["com.bar.xxx"] shouldBe "BAR"
            trie["com.foo"] shouldBe "FOO"
            trie["com.foo.xxx"] shouldBe "FOO"
            trie["com.foo.barf"] shouldBe "FOO"
            trie["com.foo.bar"] shouldBe "FOO_BAR"
            trie["com.foo.bar.quux"] shouldBe "FOO_BAR"

            trie[""] shouldBe DEFAULT
            trie["com"] shouldBe DEFAULT
            trie["com.foobar"] shouldBe DEFAULT
            trie["xxx"] shouldBe DEFAULT
        }

        @Test
        fun `when given a general case mapping with empty keys`() {
            val map = mapOf(
                "" to "EMPTY",
                "." to "DOT",
                ".." to "DOT_DOT",
                ".foo." to "FOO"
            )
            val trie = create(map, '.', DEFAULT)

            trie[""] shouldBe "EMPTY"
            trie[".foo"] shouldBe "EMPTY"
            trie[".foo.bar"] shouldBe "EMPTY"
            trie["."] shouldBe "DOT"
            trie["..foo"] shouldBe "DOT"
            trie["...foo"] shouldBe "DOT_DOT"
            trie[".foo..bar"] shouldBe "FOO"

            trie["foo"] shouldBe DEFAULT
            trie["foo.bar"] shouldBe DEFAULT
        }

        @Test
        fun `when given a general case mapping with a 'null' key`() {
            val map = mapOf(
                "foo" to "FOO",
                null to "BAD"
            )
            assertThrows<NullPointerException> {
                create(map, '.', DEFAULT)
            }
        }

        @Test
        fun `when given a general case mapping with a 'null' value`() {
            val map = hashMapOf<String, String?>()
            map["foo"] = null
            map["foo.bar"] = "FOO_BAR"
            val trie = create(map, '.', DEFAULT)

            trie["foo.bar"] shouldBe "FOO_BAR"

            trie["foo"].shouldBeNull()
            trie["foo."].shouldBeNull()
            trie["foo.barf"].shouldBeNull()

            trie[""] shouldBe DEFAULT
            trie["foo_bar"] shouldBe DEFAULT
        }
    }

    @Test
    fun `the same value when the input mapping was modified`() {
        val map = mutableMapOf("foo" to "FOO")
        val trie = create(map, '.', DEFAULT)

        trie["foo.bar"] shouldBe "FOO"

        map["foo.bar"] = "BAR" // Should not affect the trie.
        trie["foo.bar"] shouldBe "FOO"
    }
}

// For readability.
private operator fun <T> SegmentTrie<T>.get(key: String): T {
    return find(key)
}
