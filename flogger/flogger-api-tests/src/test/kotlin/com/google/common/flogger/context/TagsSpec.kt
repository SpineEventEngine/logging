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

import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class TagsSpec {

    companion object {
        fun setOf(vararg elements: Any): ImmutableSet<Any> {
            return ImmutableSet.copyOf(elements)
        }
    }

    @Test
    fun testEmpty() {
        assertThat(Tags.builder().build()).isSameInstanceAs(Tags.empty())
        assertThat(Tags.empty().asMap()).isEmpty()
    }

    @Test
    fun testSimpleTag() {
        val tags = Tags.builder().addTag("foo").build()
        assertThat(tags.asMap()).containsEntry("foo", setOf())
        assertThat(tags.asMap()).hasSize(1)
    }

    @Test
    fun testTagWithString() {
        val tags = Tags.builder().addTag("foo", "bar").build()
        assertThat(tags.asMap()).containsEntry("foo", setOf("bar"))
        assertThat(tags.asMap()).hasSize(1)
    }

    @Test
    fun testTagWithEscapableString() {
        val tags = Tags.builder().addTag("foo", "\"foo\\bar\"").build()
        assertThat(tags.asMap()).containsEntry("foo", setOf("\"foo\\bar\""))
        assertThat(tags.asMap()).hasSize(1)
    }

    @Test
    fun testTagWithBoolean() {
        val tags = Tags.builder().addTag("foo", true).build()
        assertThat(tags.asMap()).containsEntry("foo", setOf(true))
        assertThat(tags.asMap()).hasSize(1)
    }

    @Test
    fun testTagWithLong() {
        val tags = Tags.builder().addTag("foo", 42L).build()
        assertThat(tags.asMap()).containsEntry("foo", setOf(42L))
        assertThat(tags.asMap()).hasSize(1)
    }

    @Test
    fun testTagWithDouble() {
        val tags = Tags.builder().addTag("foo", 12.34).build()
        assertThat(tags.asMap()).containsEntry("foo", setOf(12.34))
        assertThat(tags.asMap()).hasSize(1)
    }

    @Test
    fun testTagWithBadName() {
        assertThrows<IllegalArgumentException> {
            Tags.builder().addTag("foo!", "bar")
        }
    }

    @Test
    fun testTagMerging_null() {
        val tags = Tags.builder().addTag("foo").build()
        assertThrows<NullPointerException> {
            tags.merge(null)
        }
    }

    @Test
    fun testTagMerging_empty() {
        // It is important to not create new instances when merging.
        val tags = Tags.builder().addTag("foo").build()
        assertThat(tags.merge(Tags.empty())).isSameInstanceAs(tags)
        assertThat(Tags.empty().merge(tags)).isSameInstanceAs(tags)
        assertThat(Tags.empty().merge(Tags.empty())).isSameInstanceAs(Tags.empty())
    }

    @Test
    fun testTagMerging_distinct() {
        val lhs = Tags.builder().addTag("foo").addTag("tag", "true").addTag("tag", true).build()
        val rhs = Tags.builder().addTag("bar").addTag("tag", 42L).addTag("tag", 42.0).build()
        val tags = lhs.merge(rhs)
        assertThat(tags.asMap()).containsEntry("foo", setOf())
        assertThat(tags.asMap()).containsEntry("bar", setOf())
        assertThat(tags.asMap()).containsEntry("tag", setOf("true", true, 42L, 42.0))
        assertThat(tags.asMap()).hasSize(3)
    }

    @Test
    fun testTagMerging_overlap() {
        val lhs = Tags.builder().addTag("tag", "abc").addTag("tag", "def").build()
        val rhs = Tags.builder().addTag("tag", "abc").addTag("tag", "xyz").build()
        assertThat(lhs.merge(rhs).asMap()).containsEntry("tag", setOf("abc", "def", "xyz"))
        assertThat(rhs.merge(lhs).asMap()).containsEntry("tag", setOf("abc", "def", "xyz"))
    }

    @Test
    fun testTagMerging_superset() {
        val lhs = Tags.builder().addTag("tag", "abc").addTag("tag", "def").build()
        val rhs = Tags.builder().addTag("tag", "abc").build()
        assertThat(lhs.merge(rhs).asMap()).containsEntry("tag", setOf("abc", "def"))
        assertThat(rhs.merge(lhs).asMap()).containsEntry("tag", setOf("abc", "def"))
    }

    @Test
    fun testTagMerging_largeNumberOfKeys() {
        val lhs = Tags.builder()
        val rhs = Tags.builder()

        repeat(256) { i ->
            val key = String.format("k%02X", i)
            if ((i and 1) == 0) {
                lhs.addTag(key)
            }
            if ((i and 2) == 0) {
                rhs.addTag(key)
            }
        }

        val tagMap = lhs.build().merge(rhs.build()).asMap()
        assertThat(tagMap).hasSize(192)  // 3/4 of 256
        assertThat(tagMap.keys)
            .containsAtLeast("k00", "k01", "k02", "k80", "kCC", "kFC", "kFD", "kFE")
            .inOrder()
        // Nothing ending in 3, 7, B or F.
        assertThat(tagMap.keys).containsNoneOf("k03", "k77", "kAB", "kFF")
    }

    @Test
    fun testTagMerging_largeNumberOfValues() {
        val lhs = Tags.builder()
        val rhs = Tags.builder()

        repeat(256) { i ->
            val value = String.format("v%02X", i)
            if ((i and 1) == 0) {
                lhs.addTag("tag", value)
            }
            if ((i and 2) == 0) {
                rhs.addTag("tag", value)
            }
        }

        val tagMap = lhs.build().merge(rhs.build()).asMap()
        assertThat(tagMap).hasSize(1)
        assertThat(tagMap).containsKey("tag")

        val values = tagMap.get("tag")
        assertThat(values).hasSize(192)  // 3/4 of 256
        assertThat(values)
            .containsAtLeast("v00", "v01", "v02", "v80", "vCC", "vFC", "vFD", "vFE")
            .inOrder()
        assertThat(tagMap.keys).containsNoneOf("v03", "v77", "vAB", "vFF")
    }

    @Test
    fun testBuilder_largeNumberOfDuplicates() {
        val tags = Tags.builder()
        repeat(256) { i ->
            tags.addTag("foo")
            tags.addTag("bar")
            repeat(20) { j ->
                val value = "v" + (5 - (j % 5))  // v5 ... v1 (reverse order)
                tags.addTag("foo", value)
                tags.addTag("bar", value)
            }
        }
        val tagMap = tags.build().asMap()
        assertThat(tagMap).hasSize(2)
        assertThat(tagMap.keys).containsExactly("bar", "foo").inOrder()
        assertThat(tagMap["foo"]).containsExactly("v1", "v2", "v3", "v4", "v5").inOrder()
        assertThat(tagMap["bar"]).containsExactly("v1", "v2", "v3", "v4", "v5").inOrder()
    }

    @Test
    fun testToString() {
        assertToString(Tags.builder(), "{}")
        assertToString(Tags.builder().addTag("foo").addTag("bar"), "{bar=[], foo=[]}")
        assertToString(Tags.builder().addTag("foo", "value"), "{foo=[value]}")
        assertToString(Tags.builder().addTag("foo", ""), "{foo=[]}")
        // Mixed types will be rare but should be sorted stably in the same order as the tag type enum:
        // boolean < string < integer < double
        assertToString(
            Tags.builder()
                .addTag("foo", "bar")
                .addTag("foo", true)
                .addTag("foo", 12.3)
                .addTag("foo", 42),
            "{foo=[true, bar, 42, 12.3]}"
        )
    }

    private fun assertToString(builder: Tags.Builder, expected: String) {
        assertThat(builder.toString()).isEqualTo(expected)
        assertThat(builder.build().toString()).isEqualTo(expected)
    }
}
