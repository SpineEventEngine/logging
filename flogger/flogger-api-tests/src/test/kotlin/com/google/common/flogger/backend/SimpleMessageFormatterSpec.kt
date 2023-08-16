/*
 * Copyright (C) 2017 The Flogger Authors.
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

import com.google.common.flogger.LogContext
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.context.Tags
import com.google.common.flogger.testing.FakeLogData
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SimpleMessageFormatterSpec {

    companion object {

        /**
         * `INT_KEY` uses `Int::class.javaObjectType` to make sure we get `Integer` class on JVM.
         *
         * Otherwise, Kotlin compiler passes `int` class for primitives. It is important because
         * metadata objects are generified, which means they would use boxed primitives.
         *
         * The same story with [BOOL_KEY].
         */
        private val INT_KEY = repeated("int", Int::class.javaObjectType)
        private val BOOL_KEY = single("bool", Boolean::class.javaObjectType)
        private val STRING_KEY = single("string", String::class.java)
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
            val logged = FakeLogData.of(LITERAL).addMetadata(LogContext.Key.LOG_CAUSE, cause)
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
            logged.addMetadata(LogContext.Key.LOG_CAUSE, cause)
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
            logged.addMetadata(LogContext.Key.TAGS, tags)
            val withTags = "answer=42 [CONTEXT int=1 int=2 string=\"Hi\" one=\"foo\" two=\"bar\" ]"
            appendFormatted(logged, scope) shouldBe withTags
        }

        @Test
        fun `to a non-empty buffer`() {
            val tags = Tags.builder()
                .addTag("two", "bar")
                .addTag("one", "foo")
                .build()
            val logged = FakeLogData.of("message").addMetadata(LogContext.Key.TAGS, tags)
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
            val logged = FakeLogData.of("msg").addMetadata(LogContext.Key.TAGS, tags)
            val scope = FakeMetadata().add(STRING_KEY, "Hi")
            val formatter = SimpleMessageFormatter.getSimpleFormatterIgnoring(STRING_KEY)

            // Cause is ignored in “simple” message formatting, and should not appear
            // in the output even though it is not explicitly ignored above.
            val cause: Throwable = IllegalArgumentException("Badness")
            logged.addMetadata(LogContext.Key.LOG_CAUSE, cause)
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
