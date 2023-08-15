/*
 * Copyright (C) 2021 The Flogger Authors.
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

package com.google.common.flogger.backend

import com.google.common.flogger.MetadataKey
import com.google.common.flogger.MetadataKey.KeyValueHandler
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.backend.MetadataKeyValueHandlers.getDefaultHandler
import com.google.common.flogger.backend.MetadataKeyValueHandlers.getDefaultRepeatedValueHandler
import com.google.common.flogger.backend.MetadataKeyValueHandlers.getDefaultValueHandler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`MetadataKeyValueHandlers` should")
internal class MetadataKeyValueHandlersSpec {

    companion object {
        private val single: MetadataKey<Any> = single("single", Any::class.java)
        private val repeated: MetadataKey<Any> = repeated("repeated", Any::class.java)
        private val ignored: MetadataKey<Any> = single("ignored", Any::class.java)
    }

    @Test
    fun `provide a handler for singleton values`() {
        val keyValueHandler = MemoizingHandler()
        val metadataHandler = getDefaultValueHandler()
        metadataHandler.handle(single, "value", keyValueHandler)
        keyValueHandler.entries shouldContainExactly listOf("single=value")
    }

    @Test
    fun `provide a handler for repeated values`() {
        val keyValueHandler = MemoizingHandler()
        val metadataHandler = getDefaultRepeatedValueHandler()

        metadataHandler.handle(repeated, iterate("foo", "bar"), keyValueHandler)

        val expected = listOf("repeated=foo", "repeated=bar")
        keyValueHandler.entries shouldContainExactlyInAnyOrder expected
    }

    @Test
    fun `provide a handler that ignores the given keys`() {
        val keyValueHandler = MemoizingHandler()
        val metadataHandler = getDefaultHandler(setOf(ignored))

        metadataHandler.handle(single, "foo", keyValueHandler)
        metadataHandler.handle(ignored, "ignored", keyValueHandler)
        metadataHandler.handleRepeated(repeated, iterate("bar", "baz"), keyValueHandler)

        val expected = listOf("single=foo", "repeated=bar", "repeated=baz")
        keyValueHandler.entries shouldContainExactlyInAnyOrder expected
    }
}

private class MemoizingHandler : KeyValueHandler {

    val entries = arrayListOf<String>()

    override fun handle(label: String?, value: Any?) {
        entries.add("$label=$value")
    }
}

private fun <T> iterate(vararg values: T): Iterator<T> = listOf(*values).iterator()
