/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.backend

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.spine.logging.backend.MetadataKeyValueHandlers.getDefaultHandler
import io.spine.logging.backend.MetadataKeyValueHandlers.getDefaultRepeatedValueHandler
import io.spine.logging.backend.MetadataKeyValueHandlers.getDefaultValueHandler
import io.spine.logging.jvm.given.MemoizingKvHandler
import io.spine.logging.jvm.given.iterate
import io.spine.logging.jvm.repeatedKey
import io.spine.logging.jvm.singleKey
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [MetadataKeyValueHandlers].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/MetadataKeyValueHandlersTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`MetadataKeyValueHandlers` should")
internal class MetadataKeyValueHandlersSpec {

    companion object {
        private val single = singleKey<Any>("single")
        private val repeated = repeatedKey<Any>("repeated")
        private val ignored = singleKey<Any>("ignored")
    }

    @Test
    fun `provide a handler for singleton values`() {
        val keyValueHandler = MemoizingKvHandler()
        val metadataHandler = getDefaultValueHandler()
        metadataHandler.handle(single, "value", keyValueHandler)
        keyValueHandler.entries shouldContainExactly listOf("single=value")
    }

    @Test
    fun `provide a handler for repeated values`() {
        val keyValueHandler = MemoizingKvHandler()
        val metadataHandler = getDefaultRepeatedValueHandler()

        metadataHandler.handle(repeated, iterate("foo", "bar"), keyValueHandler)

        val expected = listOf("repeated=foo", "repeated=bar")
        keyValueHandler.entries shouldContainExactlyInAnyOrder expected
    }

    @Test
    fun `provide a handler that ignores the given keys`() {
        val keyValueHandler = MemoizingKvHandler()
        val metadataHandler = getDefaultHandler(setOf(ignored))

        metadataHandler.handle(single, "foo", keyValueHandler)
        metadataHandler.handle(ignored, "ignored", keyValueHandler)
        metadataHandler.handleRepeated(repeated, iterate("bar", "baz"), keyValueHandler)

        val expected = listOf("single=foo", "repeated=bar", "repeated=baz")
        keyValueHandler.entries shouldContainExactlyInAnyOrder expected
    }
}
