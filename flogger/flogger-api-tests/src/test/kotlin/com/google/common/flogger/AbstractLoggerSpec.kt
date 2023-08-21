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

import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.LoggingException
import com.google.common.flogger.testing.TestLogger
import com.google.common.truth.Truth.assertThat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import java.util.*
import java.util.logging.Level
import kotlin.text.RegexOption.DOT_MATCHES_ALL
import org.junit.jupiter.api.Test

/**
 * See [LogContextSpec] for the most tests related to base logging behavior.
 */
internal class AbstractLoggerSpec {

    companion object {

        /**
         * Matches ISO 8601 date/time format.
         *
         * @see <a href="https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html">SimpleDateFormat</a>
         */
        private val ISO_TIMESTAMP_PREFIX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[-+]\\d{4}: .*".toRegex(
                DOT_MATCHES_ALL // Makes a dot match a line terminator as well.
            )
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
        val output = tapConsole {
            logger.atInfo().log("evil value: %s", bad)
        }
        assertThat(backend.logged).isEmpty()
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        output shouldContain "com.google.common.flogger.AbstractLoggerSpec.testErrorReporting"
        output shouldContain "java.lang.RuntimeException: Ooopsie"
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
        val output = tapConsole {
            logger.atInfo().log("evil value: %s", evil)
        }
        assertThat(backend.logged).isEmpty()
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        // It is in a subclass of RuntimeException in this case, so only check the message.
        output shouldContain "Ooopsie"
        // We didn't handle the inner exception, but that's fine.
        output shouldNotContain "<<IGNORED>>"
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
        val output = tapConsole {
            logger.atInfo().log("evil value: %s", bad)
        }
        // Matches AbstractLogger#MAX_ALLOWED_RECURSION_DEPTH.
        assertThat(backend.logged).hasSize(100)
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        output shouldContain "unbounded recursion in log statement"
        output shouldContain "com.google.common.flogger.AbstractLoggerSpec"
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
        val output = tapConsole {
            shouldThrow<LoggingException> {
                logger.atInfo().log("doomed to fail")
            }
        }
        assertThat(backend.logged).isEmpty()
        assertThat(output).isEmpty()
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
        val output = tapConsole {
            shouldThrow<MyError> {
                logger.atInfo().log("doomed to fail")
            }
        }
        assertThat(backend.logged).isEmpty()
        assertThat(output).isEmpty()
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
