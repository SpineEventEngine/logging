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

import com.google.common.flogger.context.Tags
import com.google.common.flogger.testing.FakeLogData
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.flogger.LogContext.Key
import io.spine.logging.flogger.repeatedKey
import io.spine.logging.flogger.singleKey
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [SimpleMessageFormatter].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/backend/SimpleMessageFormatterTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`SimpleMessageFormatter` should")
internal class SimpleMessageFormatterSpec {

    companion object {
        private val INT_KEY = repeatedKey<Int>("int")
        private val BOOL_KEY = singleKey<Boolean>("bool")
        private val STRING_KEY = singleKey<String>("string")
        private val EMPTY_SCOPE = Metadata.empty()
        private const val LITERAL = "Hello World"
    }

    @Nested
    inner class
    `format a literal` {

        @Test
        fun `without metadata`() {
            val logged = FakeLogData.of(LITERAL)
            format(logged, EMPTY_SCOPE) shouldBeSameInstanceAs LITERAL
        }

        @Test
        fun `with metadata`() {
            val logged = FakeLogData.of(LITERAL).addMetadata(BOOL_KEY, true)
            format(logged, EMPTY_SCOPE) shouldBe "$LITERAL [CONTEXT bool=true ]"
        }

        @Test
        fun `with a cause`() {
            // Having a cause is a special case and never formatted.
            val cause: Throwable = IllegalArgumentException("Badness")
            val logged = FakeLogData.of(LITERAL).addMetadata(Key.LOG_CAUSE, cause)
            format(logged, EMPTY_SCOPE) shouldBeSameInstanceAs LITERAL
        }

        @Test
        fun `with scope metadata`() {
            val logged = FakeLogData.of(LITERAL)
            val scopeData = FakeMetadata().add(INT_KEY, 42)
            format(logged, scopeData) shouldBe "$LITERAL [CONTEXT int=42 ]"
        }
    }

    @Nested
    inner class
    `append formatted log message and metadata` {

        /**
         * Parsing and basic formatting are well tested in [BaseMessageFormatterSpec].
         */
        @Test
        fun `to an empty buffer`() {
            val logged = FakeLogData.withPrintfStyle("answer=%d", 42)
            appendFormatted(logged, EMPTY_SCOPE) shouldBe "answer=42"

            val scope = FakeMetadata().add(INT_KEY, 1)
            appendFormatted(logged, scope) shouldBe "answer=42 [CONTEXT int=1 ]"

            val cause: Throwable = IllegalArgumentException("Badness")
            logged.addMetadata(Key.LOG_CAUSE, cause)
            appendFormatted(logged, scope) shouldBe "answer=42 [CONTEXT int=1 ]"

            logged.addMetadata(INT_KEY, 2)
            appendFormatted(logged, scope) shouldBe "answer=42 [CONTEXT int=1 int=2 ]"

            // Note that values are grouped by a key, and keys are emitted
            // in “encounter order” (scope first).
            scope.add(STRING_KEY, "Hi")
            appendFormatted(logged, scope) shouldBe "answer=42 [CONTEXT int=1 int=2 string=\"Hi\" ]"

            // Tags get embedded as metadata, and format in metadata order.
            // So while tag keys are ordered locally, mixing tags and metadata
            // does not result in a global ordering of context keys.
            val tags = Tags.builder()
                .addTag("two", "bar")
                .addTag("one", "foo")
                .build()
            logged.addMetadata(Key.TAGS, tags)
            val withTags = "answer=42 [CONTEXT int=1 int=2 string=\"Hi\" one=\"foo\" two=\"bar\" ]"
            appendFormatted(logged, scope) shouldBe withTags
        }

        @Test
        fun `to a non-empty buffer`() {
            val tags = Tags.builder()
                .addTag("two", "bar")
                .addTag("one", "foo")
                .build()
            val logged = FakeLogData.of("message").addMetadata(Key.TAGS, tags)
            val scope = FakeMetadata().add(STRING_KEY, "Hi")

            val formatter = SimpleMessageFormatter.getDefaultFormatter()
            val metadata = MetadataProcessor.forScopeAndLogSite(scope, logged.metadata)

            val formatted = formatter.format(logged, metadata)
            formatted shouldBe "message [CONTEXT string=\"Hi\" one=\"foo\" two=\"bar\" ]"

            val buffer = StringBuilder("PREFIX: ")
            val appended = formatter.append(logged, metadata, buffer)
            "$appended" shouldBe "PREFIX: message [CONTEXT string=\"Hi\" one=\"foo\" two=\"bar\" ]"
        }

        @Test
        fun `ignoring the given key`() {
            val tags = Tags.builder()
                .addTag("two", "bar")
                .addTag("one", "foo")
                .build()
            val logged = FakeLogData.of("msg").addMetadata(Key.TAGS, tags)
            val scope = FakeMetadata().add(STRING_KEY, "Hi")
            val formatter = SimpleMessageFormatter.getSimpleFormatterIgnoring(STRING_KEY)

            // Cause is ignored in “simple” message formatting, and should not appear
            // in the output even though it is not explicitly ignored above.
            val cause: Throwable = IllegalArgumentException("Badness")
            logged.addMetadata(Key.LOG_CAUSE, cause)
            val metadata = MetadataProcessor.forScopeAndLogSite(scope, logged.metadata)
            formatter.format(logged, metadata) shouldBe "msg [CONTEXT one=\"foo\" two=\"bar\" ]"

            val buffer = StringBuilder("PREFIX: ")
            val appended = formatter.append(logged, metadata, buffer)
            "$appended" shouldBe "PREFIX: msg [CONTEXT one=\"foo\" two=\"bar\" ]"
        }
    }
}

private fun format(logData: LogData, scope: Metadata): String {
    val metadata = MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata())
    return SimpleMessageFormatter.getDefaultFormatter().format(logData, metadata)
}

private fun appendFormatted(logData: LogData, scope: Metadata): String {
    val metadata = MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata())
    return SimpleMessageFormatter.getDefaultFormatter()
        .append(logData, metadata, StringBuilder())
        .toString()
}
