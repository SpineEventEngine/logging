/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.jul

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.LogContext.Key
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.backend.given.FakeLogData
import io.spine.logging.jvm.backend.given.FakeMetadata
import io.spine.logging.jvm.context.Tags
import io.spine.logging.jvm.parser.ParseException
import io.spine.logging.jvm.singleKey
import java.time.Instant.ofEpochMilli
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.logging.Level
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests [JulRecord].
 *
 * @see <a href="https://rb.gy/4cncp">Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`JulRecord` should")
internal class JulRecordSpec {

    companion object {
        private val INT_KEY = singleKey<Int>("int")
        private val STR_KEY = singleKey<String>("str")
        private val PATH_KEY =
            object : MetadataKey<String>("path", String::class.java, true) {
                override fun emitRepeated(values: Iterator<String>, kvh: KeyValueHandler) {
                    val joined = values.asSequence().joinToString("/")
                    kvh.handle(label, joined)
                }
            }
        private const val LITERAL = "literal message"
    }

    @Nested inner class
    `return the provided` {

        @Test
        fun `log level`() {
            val level = Level.FINER
            val data = FakeLogData("") .setLevel(level)
            val record = JulRecord.create(data, Metadata.empty())
            record.level shouldBe level
        }

        @Test
        fun `literal message`() {
            val data = FakeLogData(LITERAL)
            val record = JulRecord.create(data, Metadata.empty())
            record.message shouldBe LITERAL
            record.parameters.shouldBeEmpty()
        }

        @Test
        fun `logger name and log site info`() {
            val data = FakeLogData("")
            val record = JulRecord.create(data, Metadata.empty())
            record.loggerName shouldBe data.loggerName
            record.sourceClassName shouldBe data.logSite.className
            record.sourceMethodName shouldBe data.logSite.methodName
        }

        @Test
        fun `'Throwable' cause`() {
            val cause = Throwable("Goodbye World")
            val data = FakeLogData("").addMetadata(Key.LOG_CAUSE, cause)
            val record = JulRecord.create(data, Metadata.empty())
            record.thrown shouldBeSameInstanceAs cause
        }
    }

    @Nested inner class
    `append to message` {

        @Test
        fun `log statement metadata`() {
            val timestampNanos = 123456789000L
            val intValue = 23
            val strValue = "test value"

            val data = FakeLogData(LITERAL)
                .setTimestampNanos(timestampNanos)
                .addMetadata(INT_KEY, intValue)
                .addMetadata(STR_KEY, strValue)
            val record = JulRecord.create(data, Metadata.empty())

            val expectedMetadata = "int=$intValue str=\"$strValue\""
            record.message shouldBe "$LITERAL [CONTEXT $expectedMetadata ]"
            record.instant shouldBe ofEpochMilli(NANOSECONDS.toMillis(timestampNanos))
            record.parameters.shouldBeEmpty()
        }

        @Test
        fun `merged scope and log statement metadata`() {
            val intValue = 23
            val strValue = "test value"
            val pathTree = listOf("foo", "bar", "baz")

            val scope = FakeMetadata()
                .add(PATH_KEY, pathTree[0])
                .add(INT_KEY, intValue)
                .add(PATH_KEY, pathTree[1])
            val data = FakeLogData(LITERAL)
                .addMetadata(STR_KEY, strValue)
                .addMetadata(PATH_KEY, pathTree[2])
            val record = JulRecord.create(data, scope)

            val expectedPath = pathTree.joinToString("/")
            val expectedMetadata = "path=\"$expectedPath\" int=$intValue str=\"$strValue\""
            record.message shouldBe "$LITERAL [CONTEXT $expectedMetadata ]"
        }

        @Test
        fun `provided tags`() {
            val (foo, fooValue) = "foo" to "FOO"
            val (bar, barValue) = "bar" to "BAR"
            val baz = "baz"

            val tags = Tags.builder()
                .addTag(foo, fooValue)
                .addTag(bar, barValue)
                .addTag(baz)
                .build()
            val data = FakeLogData(LITERAL).addMetadata(Key.TAGS, tags)
            val record = JulRecord.create(data, Metadata.empty())

            // Tags are returned in alphabetical order.
            val expectedTags = "$bar=\"$barValue\" $baz=true $foo=\"$fooValue\""
            record.message shouldBe "$LITERAL [CONTEXT $expectedTags ]"
        }
    }

    @Test
    fun `handle a nullable literal message`() {
        val data = FakeLogData(null).setLevel(Level.WARNING)
        val record = JulRecord.create(data, Metadata.empty())
        record.message shouldBe "null"
        record.parameters.shouldBeEmpty()
    }

    @Test
    fun `format brace pattern`() {
        val data = FakeLogData.withBraceStyle("Answer={0}", 42)
        val record = JulRecord.create(data, Metadata.empty())
        record.message shouldBe "Answer=42"
        record.parameters.shouldBeEmpty()
    }

    @Test
    fun `format printf pattern`() {
        val pattern = "Hex=%#08x, Int=%1\$d"
        val argument = 0xC0DE
        val data = FakeLogData.withPrintfStyle(pattern, argument)
        val record = JulRecord.create(data, Metadata.empty())
        record.message shouldBe pattern.format(argument)
        record.parameters.shouldBeEmpty()
    }

    @Test
    fun `handle runtime errors happening during the logging`() {
        val passedCause = Throwable("Original Cause")
        val data = FakeLogData(LITERAL).addMetadata(Key.LOG_CAUSE, passedCause)
        val error = RuntimeException()
        val record = JulRecord.error(error, data, Metadata.empty())
        record.thrown shouldBe error
        record.message shouldNotBe LITERAL
        record.message shouldContain "message: $LITERAL"
        record.message shouldContain passedCause.message!!
    }

    @Test
    fun `handle nullable arguments`() {
        val data = FakeLogData.withPrintfStyle("value=%s", null)
        val record = JulRecord.create(data, Metadata.empty())
        record.message shouldBe "value=null"
    }

    @Test
    fun `report a missing argument`() {
        val pattern = "foo=%s, bar=%s"
        val argument = "FOO"
        val data = FakeLogData.withPrintfStyle(pattern, argument)
        val record = JulRecord.create(data, Metadata.empty())
        record.message shouldEndWith "[ERROR: MISSING LOG ARGUMENT]"
    }

    @Test
    fun `report an unused argument`() {
        val pattern = "%2\$s %s %<s %s"
        val args = arrayOf("a", "b")
        val data = FakeLogData.withPrintfStyle(pattern, *(args + "c"))
        val record = JulRecord.create(data, Metadata.empty())
        record.message shouldBe "${pattern.format(*args)} [ERROR: UNUSED LOG ARGUMENTS]"
    }

    @Test
    fun `report an unreferenced midway argument`() {
        // If an unused argument is not the last, it is called “unreferenced”.
        val pattern = "%s %<s %3\$s %<s"
        val args = arrayOf("a", "b", "c") // "b" is unused.
        val data = FakeLogData.withPrintfStyle(pattern, *args)
        val parseException = shouldThrow<ParseException> {
            JulRecord.create(data, Metadata.empty())
        }

        // In `printf` pattern, indexes start from one.
        // But in the thrown exception, they are counted from zero.
        parseException.message shouldBe "unreferenced arguments [first missing index=1]"
    }

    @Test
    fun `report the unreferenced arguments up to 32rd`() {
        // The pattern doesn't use the 32-nd argument.
        var pattern = "%s ".repeat(31) + "%33\$s"
        var arguments = arrayOfNulls<Any>(33)
        var data = FakeLogData.withPrintfStyle(pattern, arguments)
        val parseException = shouldThrow<ParseException> {
            JulRecord.create(data, Metadata.empty())
        }
        parseException.message shouldContain "unreferenced arguments [first missing index=31]"

        // Gaps above the 32nd parameter are not detected.
        pattern = "%s ".repeat(32) + "%34\$s"
        arguments = arrayOfNulls(34)
        data = FakeLogData.withPrintfStyle(pattern, arguments)
        shouldNotThrow<ParseException> {
            val record = JulRecord.create(data, Metadata.empty())
            record.message shouldNotContain "UNUSED"
        }
    }

    @Test
    fun `have string representation`() {
        // LogData may behave differently with and without arguments.
        var data = FakeLogData.withPrintfStyle("Answer=%d", 42)
        var record = JulRecord.create(data, Metadata.empty())
        var stringifiedRecord = record.toString()

        // From the `SimpleLogRecord`'s point of view,
        // we do not have arguments after formatting.
        stringifiedRecord shouldContain "  message: Answer=42"
        stringifiedRecord shouldContain "  arguments: []"
        stringifiedRecord shouldContain "  original message: Answer=%d"

        data = FakeLogData(LITERAL)
        record = JulRecord.create(data, Metadata.empty())
        stringifiedRecord = record.toString()

        stringifiedRecord shouldContain "  message: $LITERAL"
        stringifiedRecord shouldContain "  arguments: []"
        stringifiedRecord shouldContain "  original message: $LITERAL"
    }
}
