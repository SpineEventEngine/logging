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

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`Tags` should")
internal class TagsSpec {

    @Test
    fun `provide an empty instance`() {
        Tags.builder().build() shouldBeSameInstanceAs Tags.empty()
        Tags.empty().asMap().shouldBeEmpty()
    }

    @Test
    fun `handle a single tag without value`() {
        val tags = Tags.builder()
            .addTag("foo")
            .build()
        tags.asMap().shouldContain("foo", setOf())
        tags.asMap() shouldHaveSize 1
    }

    @Test
    fun `handle a single tag with 'String' value`() {
        val tags = Tags.builder()
            .addTag("foo", "bar")
            .build()
        tags.asMap().shouldContain("foo", setOf("bar"))
        tags.asMap() shouldHaveSize 1
    }

    @Test
    fun `handle a single tag with escapable 'String' value`() {
        val tags = Tags.builder()
            .addTag("foo", "\"foo\\bar\"")
            .build()
        tags.asMap().shouldContain("foo", setOf("\"foo\\bar\""))
        tags.asMap() shouldHaveSize 1
    }

    @Test
    fun `handle a single tag with 'Bool' value`() {
        val tags = Tags.builder()
            .addTag("foo", true)
            .build()
        tags.asMap().shouldContain("foo", setOf(true))
        tags.asMap() shouldHaveSize 1
    }

    @Test
    fun `handle a single tag with 'Long' value`() {
        val tags = Tags.builder()
            .addTag("foo", 42L)
            .build()
        tags.asMap().shouldContain("foo", setOf(42L))
        tags.asMap() shouldHaveSize 1
    }

    @Test
    fun `handle a single tag with 'Double' value`() {
        val tags = Tags.builder()
            .addTag("foo", 12.34)
            .build()
        tags.asMap().shouldContain("foo", setOf(12.34))
        tags.asMap() shouldHaveSize 1
    }

    @Test
    fun `fail when given an incorrect tag name`() {
        // An identifier must contain only ASCII letters, digits or underscore.
        assertThrows<IllegalArgumentException> {
            Tags.builder()
                .addTag("foo!", "bar")
        }
    }

    @Test
    fun `fail on attempt to merge with 'null'`() {
        val tags = Tags.builder()
            .addTag("foo")
            .build()
        assertThrows<NullPointerException> {
            tags.merge(null)
        }
    }

    @Test
    fun `not create new instances when merging`() {
        val tags = Tags.builder()
            .addTag("foo")
            .build()
        tags.merge(Tags.empty()) shouldBeSameInstanceAs tags
        Tags.empty().merge(tags) shouldBeSameInstanceAs tags
        Tags.empty().merge(Tags.empty()) shouldBeSameInstanceAs Tags.empty()
    }

    @Test
    fun `merge with different values for a single key`() {
        val lhs = Tags.builder()
            .addTag("foo")
            .addTag("tag", "true")
            .addTag("tag", true)
            .build()
        val rhs = Tags.builder()
            .addTag("bar")
            .addTag("tag", 42L)
            .addTag("tag", 42.0)
            .build()
        val tags = lhs.merge(rhs)
        tags.asMap().shouldContain("foo", setOf())
        tags.asMap().shouldContain("bar", setOf())
        tags.asMap().shouldContain("tag", setOf("true", true, 42L, 42.0))
        tags.asMap() shouldHaveSize 3
    }

    @Test
    fun `merge with overlapping keys`() {
        val lhs = Tags.builder()
            .addTag("tag", "abc")
            .addTag("tag", "def")
            .build()
        val rhs = Tags.builder()
            .addTag("tag", "abc")
            .addTag("tag", "xyz")
            .build()
        lhs.merge(rhs).asMap().shouldContain("tag", setOf("abc", "def", "xyz"))
        rhs.merge(lhs).asMap().shouldContain("tag", setOf("abc", "def", "xyz"))
    }

    @Test
    fun `merge with its superset`() {
        val lhs = Tags.builder()
            .addTag("tag", "abc")
            .addTag("tag", "def")
            .build()
        val rhs = Tags.builder()
            .addTag("tag", "abc")
            .build()
        lhs.merge(rhs).asMap().shouldContain("tag", setOf("abc", "def"))
        rhs.merge(lhs).asMap().shouldContain("tag", setOf("abc", "def"))
    }

    @Test
    fun `merge large numbers of keys`() {
        val lhs = Tags.builder()
        val rhs = Tags.builder()

        repeat(256) { i ->
            val key = "k%02X".format(i)
            if ((i and 1) == 0) {
                lhs.addTag(key)
            }
            if ((i and 2) == 0) {
                rhs.addTag(key)
            }
        }

        val tagMap = lhs.build().merge(rhs.build()).asMap()
        tagMap shouldHaveSize 192  // 3/4 of 256

        val keys = tagMap.keys
        keys.shouldContainInOrder("k00", "k01", "k02", "k80", "kCC", "kFC", "kFD", "kFE")
        keys.shouldNotContainAnyOf("k03", "k77", "kAB", "kFF") // Nothing ends in 3, 7, B or F.
    }

    @Test
    fun `merge large numbers of values`() {
        val lhs = Tags.builder()
        val rhs = Tags.builder()

        repeat(256) { i ->
            val value = "v%02X".format(i)
            if ((i and 1) == 0) {
                lhs.addTag("tag", value)
            }
            if ((i and 2) == 0) {
                rhs.addTag("tag", value)
            }
        }

        val tagMap = lhs.build().merge(rhs.build()).asMap()
        tagMap shouldHaveSize 1
        tagMap shouldContainKey "tag"

        val values = tagMap["tag"]!!
        values shouldHaveSize 192  // 3/4 of 256
        values.shouldContainInOrder("v00", "v01", "v02", "v80", "vCC", "vFC", "vFD", "vFE")

        val keys = tagMap.keys
        keys.shouldNotContainAnyOf("v03", "v77", "vAB", "vFF")
    }

    @Test
    fun `build a new instance with a large number of duplicates`() {
        val tags = Tags.builder()

        repeat(256) {
            tags.addTag("foo")
            tags.addTag("bar")
            repeat(20) { j ->
                val value = "v" + (5 - (j % 5))  // v5 ... v1 (reverse order)
                tags.addTag("foo", value)
                tags.addTag("bar", value)
            }
        }

        val tagMap = tags.build().asMap()
        tagMap shouldHaveSize 2

        // Sets can only be compared to sets, unless both types provide a stable iteration order.
        tagMap.keys.shouldContainExactlyInAnyOrder("bar", "foo")
        tagMap["foo"].shouldContainExactlyInAnyOrder("v1", "v2", "v3", "v4", "v5")
        tagMap["bar"].shouldContainExactlyInAnyOrder("v1", "v2", "v3", "v4", "v5")
    }

    @Test
    fun `provide 'String' representation`() {
        "${Tags.builder()}" shouldBe  "{}"
        "${Tags.builder().addTag("foo").addTag("bar")}" shouldBe "{bar=[], foo=[]}"
        "${Tags.builder().addTag("foo", "value")}" shouldBe "{foo=[value]}"
        "${Tags.builder().addTag("foo", "")}" shouldBe  "{foo=[]}"

        // Mixed types will be rare but should be sorted stably in the same order
        // as the tag type enum: boolean < string < integer < double.
        val mixedTags = Tags.builder()
            .addTag("foo", "bar")
            .addTag("foo", true)
            .addTag("foo", 12.3)
            .addTag("foo", 42)

        "$mixedTags" shouldBe "{foo=[true, bar, 42, 12.3]}"
    }
}
