/*
 * Copyright (C) 2019 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.google.common.flogger.context

import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class SegmentTrieSpec {

    companion object {
        private const val DEFAULT = "DEFAULT"
    }

    @Test
    fun testEmptyMap() {
        val map = ImmutableMap.of<String, String>()
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("")).isEqualTo(DEFAULT)
        assertThat(trie.find(".")).isEqualTo(DEFAULT)
        assertThat(trie.find("foo")).isEqualTo(DEFAULT)
    }

    @Test
    fun testSingletonMap() {
        val map = ImmutableMap.of("com.foo", "FOO")
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("com.foo")).isEqualTo("FOO")
        assertThat(trie.find("com.foo.xxx")).isEqualTo("FOO")

        assertThat(trie.find("")).isEqualTo(DEFAULT)
        assertThat(trie.find("com")).isEqualTo(DEFAULT)
        assertThat(trie.find("com.foobar")).isEqualTo(DEFAULT)
        assertThat(trie.find("xxx")).isEqualTo(DEFAULT)
    }

    @Test
    fun testSingletonMap_emptysegments() {
        val map = ImmutableMap.of("...", "DOT")
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("...")).isEqualTo("DOT")
        assertThat(trie.find("....")).isEqualTo("DOT")
        assertThat(trie.find(".....")).isEqualTo("DOT")
        assertThat(trie.find("....x")).isEqualTo("DOT")

        assertThat(trie.find("")).isEqualTo(DEFAULT)
        assertThat(trie.find(".")).isEqualTo(DEFAULT)
        assertThat(trie.find("..")).isEqualTo(DEFAULT)
        assertThat(trie.find("x...")).isEqualTo(DEFAULT)
        assertThat(trie.find(".x..")).isEqualTo(DEFAULT)
        assertThat(trie.find("..x.")).isEqualTo(DEFAULT)
        assertThat(trie.find("...x")).isEqualTo(DEFAULT)
    }

    @Test
    fun testSingletonMap_emptykey() {
        val map = ImmutableMap.of("", "FOO")
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("")).isEqualTo("FOO")
        assertThat(trie.find(".")).isEqualTo("FOO")
        assertThat(trie.find("..")).isEqualTo("FOO")
        assertThat(trie.find(".x")).isEqualTo("FOO")

        assertThat(trie.find("x")).isEqualTo(DEFAULT)
        assertThat(trie.find("x.")).isEqualTo(DEFAULT)
        assertThat(trie.find("x..")).isEqualTo(DEFAULT)
        assertThat(trie.find("x.y")).isEqualTo(DEFAULT)
    }

    @Test
    fun testSingletonMap_nullkey() {
        val map = hashMapOf<String?, String>()
        map[null] = "BAD"
        try {
            @SuppressWarnings("unused") val unused = SegmentTrie.create(map, '.', DEFAULT)
            fail("expected NullPointerException")
        } catch (e: NullPointerException) {
            // pass
        }
    }

    @Test
    fun testSingletonMap_nullvalue() {
        val map = hashMapOf<String, String?>()
        map["com.foo"] = null
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("com.foo")).isNull()
        assertThat(trie.find("com.foo.xxx")).isNull()

        assertThat(trie.find("com")).isEqualTo(DEFAULT)
        assertThat(trie.find("com.foobar")).isEqualTo(DEFAULT)
        assertThat(trie.find("xxx")).isEqualTo(DEFAULT)
    }

    @Test
    fun testGeneralCaseMap() {
        val map = ImmutableMap.of(
            "com.bar", "BAR", "com.foo", "FOO", "com.foo.bar", "FOO_BAR", "com.quux", "QUUX"
        )
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("com.bar")).isEqualTo("BAR")
        assertThat(trie.find("com.bar.xxx")).isEqualTo("BAR")
        assertThat(trie.find("com.foo")).isEqualTo("FOO")
        assertThat(trie.find("com.foo.xxx")).isEqualTo("FOO")
        assertThat(trie.find("com.foo.barf")).isEqualTo("FOO")
        assertThat(trie.find("com.foo.bar")).isEqualTo("FOO_BAR")
        assertThat(trie.find("com.foo.bar.quux")).isEqualTo("FOO_BAR")

        assertThat(trie.find("")).isEqualTo(DEFAULT)
        assertThat(trie.find("com")).isEqualTo(DEFAULT)
        assertThat(trie.find("com.foobar")).isEqualTo(DEFAULT)
        assertThat(trie.find("xxx")).isEqualTo(DEFAULT)
    }

    @Test
    fun testGeneralCaseMap_emptysegments() {
        val map = ImmutableMap.of(
            "", "EMPTY", ".", "DOT", "..", "DOT_DOT", ".foo.", "FOO"
        )
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("")).isEqualTo("EMPTY")
        assertThat(trie.find(".foo")).isEqualTo("EMPTY")
        assertThat(trie.find(".foo.bar")).isEqualTo("EMPTY")
        assertThat(trie.find(".")).isEqualTo("DOT")
        assertThat(trie.find("..foo")).isEqualTo("DOT")
        assertThat(trie.find("...foo")).isEqualTo("DOT_DOT")
        assertThat(trie.find(".foo..bar")).isEqualTo("FOO")

        assertThat(trie.find("foo")).isEqualTo(DEFAULT)
        assertThat(trie.find("foo.bar")).isEqualTo(DEFAULT)
    }

    @Test
    fun testGeneralCaseMap_nullkeys() {
        val map = hashMapOf<String?, String>()
        map["foo"] = "FOO"
        map[null] = "BAD"
        try {
            @SuppressWarnings("unused")
            val unused = SegmentTrie.create(map, '.', DEFAULT)
            fail("expected NullPointerException")
        } catch (e: NullPointerException) {
            // pass
        }
    }

    @Test
    fun testGeneralCaseMap_nullvalues() {
        val map = hashMapOf<String, String?>()
        map["foo"] = null
        map["foo.bar"] = "FOO_BAR"
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("foo.bar")).isEqualTo("FOO_BAR")

        assertThat(trie.find("foo")).isNull()
        assertThat(trie.find("foo.")).isNull()
        assertThat(trie.find("foo.barf")).isNull()

        assertThat(trie.find("")).isEqualTo(DEFAULT)
        assertThat(trie.find("foo_bar")).isEqualTo(DEFAULT)
    }

    @Test
    fun testImmutable() {
        val map = hashMapOf<String, String>()
        map["foo"] = "FOO"
        val trie = SegmentTrie.create(map, '.', DEFAULT)

        assertThat(trie.find("foo.bar")).isEqualTo("FOO")

        // No change if source map modified.
        map["foo.bar"] = "BAR"
        assertThat(trie.find("foo.bar")).isEqualTo("FOO")
    }
}
