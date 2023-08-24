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

package com.google.common.flogger.backend

import com.google.common.flogger.MetadataKey
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.backend.MetadataKeyValueHandlers.getDefaultHandler
import com.google.common.flogger.backend.MetadataKeyValueHandlers.getDefaultRepeatedValueHandler
import com.google.common.flogger.backend.MetadataKeyValueHandlers.getDefaultValueHandler
import com.google.common.flogger.given.MemoizingKvHandler
import com.google.common.flogger.given.iterate
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [MetadataKeyValueHandlers].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/backend/MetadataKeyValueHandlersTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`MetadataKeyValueHandlers` should")
internal class MetadataKeyValueHandlersSpec {

    companion object {
        private val single: MetadataKey<Any> = single("single", Any::class.java)
        private val repeated: MetadataKey<Any> = repeated("repeated", Any::class.java)
        private val ignored: MetadataKey<Any> = single("ignored", Any::class.java)
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
