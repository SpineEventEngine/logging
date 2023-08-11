/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.MetadataKey
import com.google.common.flogger.testing.MetadataSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class ContextMetadataSpec {

    companion object {
        private val FOO_KEY: MetadataKey<String> =
            MetadataKey.single("FOO", String::class.java)
        private val BAR_KEY: MetadataKey<String> = MetadataKey.repeated("BAR", String::class.java)
        private val UNUSED_KEY: MetadataKey<String> =
            MetadataKey.single("UNUSED", String::class.java)
    }

    @Test
    fun testNone() {
        assertThat(ContextMetadata.none()).hasSize(0)
    }

    @Test
    fun testSingleton() {
        val metadata = ContextMetadata.singleton(FOO_KEY, "foo")
        assertThat(metadata).hasSize(1)
        assertThat(metadata).containsEntries(FOO_KEY, "foo")
        assertThat(metadata.findValue(UNUSED_KEY)).isNull()
    }

    @Test
    fun testBuilder() {
        val metadata = ContextMetadata.builder()
            .add(FOO_KEY, "one")
            .add(BAR_KEY, "two")
            .add(BAR_KEY, "three")
            .add(FOO_KEY, "four")
            .build()
        assertThat(metadata).hasSize(4)
        assertThat(metadata).containsEntries(FOO_KEY, "one", "four")
        assertThat(metadata).containsEntries(BAR_KEY, "two", "three")
        // The most recent single keyed value.
        assertThat(metadata.findValue(FOO_KEY)).isEqualTo("four")
        assertThat(metadata.findValue(UNUSED_KEY)).isNull()
        try {
            metadata.findValue(BAR_KEY)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // pass
        }
    }

    @Test
    fun testConcatenate_none() {
        val metadata = ContextMetadata.singleton(FOO_KEY, "foo")
        assertThat(ContextMetadata.none().concatenate(metadata)).isSameInstanceAs(metadata)
        assertThat(metadata.concatenate(ContextMetadata.none())).isSameInstanceAs(metadata)
    }

    @Test
    fun testConcatenate_duplicateSingleKey() {
        val metadata = ContextMetadata.singleton(FOO_KEY, "foo")
            .concatenate(ContextMetadata.singleton(FOO_KEY, "bar"))
        assertThat(metadata).hasSize(2)
        // No reordering, no de-duplication.
        assertThat(metadata).containsEntries(FOO_KEY, "foo", "bar")
    }

    @Test
    fun testConcatenate_general() {
        val first =
            ContextMetadata.builder()
                .add(FOO_KEY, "one")
                .add(BAR_KEY, "two")
                .build()
        val second =
            ContextMetadata.builder()
                .add(BAR_KEY, "three")
                .add(FOO_KEY, "four")
                .build()
        val metadata = first.concatenate(second)
        assertThat(metadata).hasSize(4)
        assertThat(metadata).containsEntries(FOO_KEY, "one", "four")
        assertThat(metadata).containsEntries(BAR_KEY, "two", "three")
    }
}
