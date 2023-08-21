/*
 * Copyright (C) 2021 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.google.common.flogger

import com.google.common.base.Splitter
import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.LoggingException
import com.google.common.flogger.testing.TestLogger
import com.google.common.truth.Truth.assertThat
import io.kotest.assertions.throwables.shouldThrow
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Consumer
import java.util.logging.Level
import java.util.regex.Pattern
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/* See LogContextTest.java for the most tests related to base logging behaviour. */
internal class AbstractLoggerSpec {

    private val err = ByteArrayOutputStream()
    private var systemErr: PrintStream? = null

    @BeforeEach
    @Throws(UnsupportedEncodingException::class)
    fun redirectStderr() {
        systemErr = System.err
        System.setErr(PrintStream(err, true, StandardCharsets.UTF_8.name()))
    }

    @AfterEach
    fun restoreStderr() {
        System.setErr(systemErr)
    }

    @Test
    fun testErrorReporting() {
        val backend = TestBackend()
        val logger = TestLogger.create(backend)
        val bad: Any = object : Any() {
            override fun toString(): String {
                throw RuntimeException("Ooopsie")
            }
        }
        logger.atInfo().log("evil value: %s", bad)
        assertThat(backend.logged).isEmpty()
        val stdErrLines = Splitter.on(System.lineSeparator()).splitToList(stdErrString())
        assertThat(stdErrLines).isNotEmpty()
        val errLine = stdErrLines[0]
        assertThat(errLine).matches(ISO_TIMESTAMP_PREFIX)
        assertThat(errLine).contains("logging error")
        assertThat(errLine).contains("com.google.common.flogger.AbstractLoggerSpec.testErrorReporting")
        assertThat(errLine).contains("java.lang.RuntimeException: Ooopsie")
    }

    @Test
    fun testBadError() {
        val backend = TestBackend()
        val logger = TestLogger.create(backend)
        // A worst case scenario whereby an object's toString() throws an exception which itself throws
        // an exception. If we can handle this, we can handle just about anything!
        val evil: Any = object : Any() {
            override fun toString(): String {
                throw object : RuntimeException("Ooopsie") {
                    private val serialVersionUID: Long = -5383608141374997920L
                    override fun toString(): String {
                        throw RuntimeException("<<IGNORED>>")
                    }
                }
            }
        }
        logger.atInfo().log("evil value: %s", evil)
        assertThat(backend.logged).isEmpty()
        val stdErrLines = Splitter.on(System.lineSeparator()).splitToList(stdErrString())
        assertThat(stdErrLines).isNotEmpty()
        val errLine = stdErrLines[0]
        assertThat(errLine).matches(ISO_TIMESTAMP_PREFIX)
        assertThat(errLine).contains("logging error")
        // It is in a subclass of RuntimeException in this case, so only check the message.
        assertThat(errLine).contains("Ooopsie")
        // We didn't handle the inner exception, but that's fine.
        stdErrLines.forEach(Consumer { line: String? ->
            assertThat(
                line
            ).doesNotContain("<<IGNORED>>")
        })
    }

    @Test
    fun testRecurionHandling() {
        val backend = TestBackend()
        // The test logger does not handle errors gracefully, which should trigger the fallback error
        // handling in AbstractLogger (that is what we want to test).
        val logger = TestLogger.create(backend)
        val bad: Any = object : Any() {
            override fun toString(): String {
                logger.atInfo().log("recursion: %s", this)
                return "<unused>"
            }
        }
        logger.atInfo().log("evil value: %s", bad)
        // Matches AbstractLogger#MAX_ALLOWED_RECURSION_DEPTH.
        assertThat(backend.logged).hasSize(100)
        val stdErrLines = Splitter.on(System.lineSeparator()).splitToList(stdErrString())
        assertThat(stdErrLines).isNotEmpty()
        val errLine = stdErrLines[0]
        assertThat(errLine).matches(ISO_TIMESTAMP_PREFIX)
        assertThat(errLine).contains("logging error")
        assertThat(errLine).contains("unbounded recursion in log statement")
        assertThat(errLine).contains("com.google.common.flogger.AbstractLoggerSpec")
    }

    @Test
    fun testLoggingExceptionAllowed() {
        // A backend that deliberately triggers an internal error.
        val backend = object : TestBackend() {
            override fun log(data: LogData) {
                throw LoggingException("Allowed")
            }
        }
        val logger = TestLogger.create(backend)
        shouldThrow<LoggingException> {
            logger.atInfo().log("doomed to fail")
        }
        assertThat(backend.logged).isEmpty()
        assertThat(stdErrString()).isEmpty()
    }

    @Test
    fun testLoggingErrorAllowed() {
        // A backend that triggers an Error of some kind.
        val backend: TestBackend = object : TestBackend() {
            override fun log(data: LogData) {
                throw MyError("Allowed")
            }
        }
        val logger = TestLogger.create(backend)
        shouldThrow<MyError> {
            logger.atInfo().log("doomed to fail")
        }
        assertThat(backend.logged).isEmpty()
        assertThat(stdErrString()).isEmpty()
    }

    private fun stdErrString(): String {
        return try {
            err.toString(StandardCharsets.UTF_8.name())
        } catch (impossible: UnsupportedEncodingException) {
            throw AssertionError(impossible)
        }
    }

    companion object {
        // Matches ISO 8601 date/time format.
        // See: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
        private val ISO_TIMESTAMP_PREFIX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[-+]\\d{4}: .*")
    }
}

private open class TestBackend : LoggerBackend() {
    val logged: MutableList<String?> = ArrayList()
    override fun getLoggerName(): String {
        return "<unused>"
    }

    override fun isLoggable(lvl: Level): Boolean {
        return true
    }

    // Format without using Flogger util classes, so we can test what happens if arguments cause
    // errors (the core utility classes handle this properly).
    override fun log(data: LogData) {
        if (data.getTemplateContext() != null) {
            logged.add(
                String.format(
                    Locale.ROOT, data.getTemplateContext().message, *data.getArguments()
                )
            )
        } else {
            logged.add(data.getLiteralArgument().toString())
        }
    }

    // Don't handle any errors in the backend, so we can test “last resort” error handling.
    override fun handleError(error: RuntimeException, badData: LogData) {
        throw error
    }
}

// Needed for testing error handling since you can't catch raw “Error” in tests.
private class MyError(message: String?) : Error(message) {
    private val serialVersionUID: Long = -9141474175879098403L
}
