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

package io.spine.logging.backend.system

import com.google.common.flogger.parser.ParseException
import com.google.common.flogger.testing.AssertingLogger
import com.google.common.flogger.testing.FakeLogData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import java.util.*
import java.util.TimeZone.getTimeZone
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests [SimpleLoggerBackend].
 *
 * @see <a href="https://github.com/google/flogger/blob/70c5aea863952ee61b3d33afb41f2841b6d63455/api/src/test/java/com/google/common/flogger/backend/system/SimpleBackendLoggerTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`SimpleLoggerBackend` should")
internal class SimpleLoggerBackendSpec {

    private val logger = AssertingLogger()
    private val backend = SimpleLoggerBackend(logger)

    @Test
    fun `log literals`() {
        val literal = "Literal"
        val data = FakeLogData.of(literal)
        backend.log(data)
        logger.lastLogged shouldBe literal
    }

    @Nested
    inner class
    `log printf formatted messages` {

        @Test
        fun `substituting a single argument`() {
            val pattern = "Hello %s World"
            val argument = "Printf"
            val expected = pattern.format(argument)
            val data = FakeLogData.withPrintfStyle(pattern, argument)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `substituting multiple arguments`() {
            val pattern = "%s %d %f"
            val arguments = arrayOf("Foo", 12345678, 1234.5678)
            val expected = pattern.format(*arguments)
            val data = FakeLogData.withPrintfStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with a calendar`() {
            val calendar = GregorianCalendar(1985, 6, 13, 5, 20, 3).apply {
                setTimeZone(getTimeZone("GMT"))
            }
            val pattern = "date=%1\$tD %1\$tr"
            val expected = pattern.format(calendar)
            val data = FakeLogData.withPrintfStyle(pattern, calendar)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with formatted arguments`() {
            val pattern = "Hello %#08X %+,8d %8.2f World"
            val arguments = arrayOf(0xcafe, 1234, -12.0)
            val expected = pattern.format(*arguments)
            val data = FakeLogData.withPrintfStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with a 'Formattable'`() {
            val formattable = Formattable { fmt, flags, width, precision ->
                fmt.format("[f=%d, w=%d, p=%d]", flags, width, precision)
            }
            val cases = mapOf(
                "Hello %s World" to "Hello [f=0, w=-1, p=-1] World",
                "Hello %#S World" to "Hello [f=6, w=-1, p=-1] World",
                "Hello %-10.4s World" to "Hello [f=1, w=10, p=4] World"
            )
            cases.forEach { (pattern, expected) ->
                val data = FakeLogData.withPrintfStyle(pattern, formattable)
                backend.log(data)
                logger.lastLogged shouldBe expected
            }
        }

        @Test
        fun `with a hashcode`() {
            val any = object : Any() {
                override fun hashCode(): Int {
                    return -0x21524111
                }
            }
            val cases = listOf("hash=%h", "%-10H")
            cases.forEach { pattern ->
                val data = FakeLogData.withPrintfStyle(pattern, any)
                val expected = pattern.format(any)
                backend.log(data)
                logger.lastLogged shouldBe expected
            }
        }

        @Test
        fun `with a nullable argument`() {
            // Typed variable is needed to disambiguate `log(String, Object)`
            // and `log(String, Object[])`.
            val nullArgument: Any? = null

            // `%h` and `%t` trigger different types of parameters,
            // so it is worth checking them as well.
            val cases = listOf(
                "[%6.2f] #1",
                "[%-10H] #2",
                "[%8tc] #3"
            )

            cases.forEachIndexed { index, pattern ->
                val data = FakeLogData.withPrintfStyle(pattern, nullArgument)
                val expected = "[null] #${index + 1}"
                backend.log(data)
                logger.lastLogged shouldBe expected
            }
        }

        @Test
        fun `with a line separator`() {
            val lineSeparator = System.getProperty("line.separator").also {
                it shouldMatch "\\n|\\r(?:\\n)?"
            }
            val pattern = "Hello %n World"
            val expected = pattern.format()
            val data = FakeLogData.withPrintfStyle(pattern)
            backend.log(data)
            logger.lastLogged shouldBe expected
            logger.lastLogged shouldContain lineSeparator
        }

        @Test
        fun `handling incorrect pattern`() {
            val pattern = "Hello %?X World"
            val data = FakeLogData.withPrintfStyle(pattern)
            val parseException = shouldThrow<ParseException> {
                backend.log(data)
            }
            logger.logged.shouldBeEmpty()
            backend.handleError(parseException, data)
            logger.logged shouldHaveSize 1
            logger.lastLogged shouldContain parseException.message!!
            logger.lastLogged shouldContain pattern
        }

        @Test
        fun `with re-ordered arguments`() {
            val pattern = "%3\$d %2\$d %1\$d"
            val arguments = arrayOf(1, 2, 3)
            val expected = pattern.format(*arguments)
            val data = FakeLogData.withPrintfStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with repeated arguments`() {
            val pattern = "%d %2\$d %2\$d"
            val arguments = arrayOf(1, 2)
            val expected = pattern.format(*arguments)
            val data = FakeLogData.withPrintfStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `referencing the previous argument`() {
            val pattern = "%d %d %<d %<d"
            val arguments = arrayOf(1, 2)
            val expected = pattern.format(*arguments)
            val data = FakeLogData.withPrintfStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }
    }

    @Nested
    inner class
    `log brace formatted messages` {

        @Test
        fun `substituting a single argument`() {
            val pattern = "Hello {0} World"
            val argument = "Brace"
            val expected = "Hello Brace World"
            val data = FakeLogData.withBraceStyle(pattern, argument)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `substituting multiple arguments`() {
            val pattern = "{0} {1} {2}"
            val arguments = arrayOf("Foo", 12345678, 1234.5678)
            val expected = "Foo 12,345,678 1,234.567800"
            val data = FakeLogData.withBraceStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with a calendar`() {
            val calendar = GregorianCalendar(1985, 6, 13, 5, 20, 3)
            calendar.setTimeZone(getTimeZone("GMT"))
            val pattern = "date={0}"
            val expected = "date=Sat Jul 13 05:20:03 GMT 1985"
            val data = FakeLogData.withBraceStyle(pattern, calendar)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with a nullable argument`() {
            // Typed variable is needed to disambiguate `log(String, Object)`
            // and `log(String, Object[])`.
            val nullArgument: Any? = null
            val pattern = "[{0}]"
            val expected = "[null]"
            val data = FakeLogData.withBraceStyle(pattern, nullArgument)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with re-ordered arguments`() {
            val pattern = "{2} {1} {0}"
            val arguments = arrayOf(1, 2, 3)
            val expected = "3 2 1"
            val data = FakeLogData.withBraceStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }

        @Test
        fun `with repeated arguments`() {
            val pattern = "{0} {0} {1}"
            val arguments = arrayOf(1, 2)
            val expected = "1 1 2"
            val data = FakeLogData.withBraceStyle(pattern, *arguments)
            backend.log(data)
            logger.lastLogged shouldBe expected
        }
    }
}
