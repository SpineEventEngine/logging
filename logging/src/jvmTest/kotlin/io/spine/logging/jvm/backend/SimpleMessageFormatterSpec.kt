/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend

import io.kotest.matchers.shouldBe
import io.spine.logging.jvm.backend.given.FakeLogData
import io.spine.logging.jvm.backend.given.FakeMetadata
import io.spine.logging.jvm.repeatedKey
import io.spine.logging.jvm.singleKey
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [SimpleMessageFormatter].
 */
@DisplayName("`SimpleMessageFormatter` should")
internal class SimpleMessageFormatterSpec {

    private val formatter = SimpleMessageFormatter.getDefaultFormatter()

    @Nested
    inner class `format messages` {

        @Test
        fun `without metadata`() {
            val logData = FakeLogData("Hello World")
            val metadata = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), Metadata.empty())
            val result = formatter.format(logData, metadata)
            result shouldBe "Hello World"
        }

        @Test
        fun `with empty metadata`() {
            val logData = FakeLogData("Test message")
            val emptyMetadata = FakeMetadata()
            val processor = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), emptyMetadata)
            val result = formatter.format(logData, processor)
            result shouldBe "Test message"
        }

        @Test
        fun `with single metadata keys`() {
            val strKey = singleKey<String>("str")
            val intKey = singleKey<Int>("count")

            val logData = FakeLogData("Message")
            val metadata = FakeMetadata()
                .add(strKey, "value")
                .add(intKey, 42)

            val processor = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), metadata)
            val result = formatter.format(logData, processor)
            result shouldBe "Message [CONTEXT str=\"value\" count=42 ]"
        }

        @Test
        fun `with repeated metadata key having single value`() {
            val repeatedKey = repeatedKey<Int>("numbers")

            val logData = FakeLogData("Data")
            val metadata = FakeMetadata()
                .add(repeatedKey, 123)

            val processor = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), metadata)
            val result = formatter.format(logData, processor)
            result shouldBe "Data [CONTEXT numbers=123 ]"
        }

        @Test
        fun `with repeated metadata key having multiple values`() {
            val repeatedKey = repeatedKey<String>("items")

            val logData = FakeLogData("List")
            val metadata = FakeMetadata()
                .add(repeatedKey, "first")
                .add(repeatedKey, "second")
                .add(repeatedKey, "third")

            val processor = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), metadata)
            val result = formatter.format(logData, processor)
            result shouldBe "List [CONTEXT items=[\"first\", \"second\", \"third\"] ]"
        }

        @Test
        fun `matching Log4j2 backend test case`() {
            // This replicates the exact test case that was failing in Log4j2LoggerBackendSpec
            val intKey = repeatedKey<Int>("int")
            val strKey = singleKey<String>("str")

            val logData = FakeLogData("Foo='bar'")
            val metadata = FakeMetadata()
                .add(intKey, 23)
                .add(strKey, "str value")

            val processor = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), metadata)
            val result = formatter.format(logData, processor)
            result shouldBe "Foo='bar' [CONTEXT int=23 str=\"str value\" ]"
        }

        @Test
        fun `with null literal argument`() {
            val logData = FakeLogData(null)
            val metadata = MetadataProcessor.forScopeAndLogSite(Metadata.empty(), Metadata.empty())
            val result = formatter.format(logData, metadata)
            result shouldBe ""
        }
    }
}
