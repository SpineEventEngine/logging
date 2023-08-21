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
import com.google.common.flogger.backend.LoggingException
import com.google.common.flogger.given.MemoizingBackend
import com.google.common.flogger.testing.TestLogger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.spine.testing.logging.tapConsole
import kotlin.text.RegexOption.DOT_MATCHES_ALL
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * See [LogContextSpec] for the most tests related to base logging behavior.
 */
@DisplayName("`AbstractLogger` should")
internal class AbstractLoggerSpec {

    private val backend = MemoizingBackend()
    private val logger = TestLogger.create(backend)

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
    fun `report an error`() {
        val bad: Any = object : Any() {
            override fun toString(): String = error("Ooopsie")
        }
        val output = tapConsole {
            logger.atInfo().log("bad value: %s", bad)
        }
        backend.logged.shouldBeEmpty()
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        output shouldContain "com.google.common.flogger.AbstractLoggerSpec.report error"
        output shouldContain "java.lang.IllegalStateException: Ooopsie"
    }

    @Test
    fun `report an inner error`() {
        // A worst case scenario whereby an object's `toString()` throws an exception,
        // which itself throws an exception. If we can handle this, we can handle
        // just about anything!
        val bad: Any = object : Any() {
            override fun toString(): String = throw object : IllegalStateException("Ooopsie") {
                private val serialVersionUID: Long = -5383608141374997920L
                override fun toString(): String = error("<<IGNORED>>")
            }
        }
        val output = tapConsole {
            logger.atInfo().log("bad value: %s", bad)
        }
        backend.logged.shouldBeEmpty()
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        output shouldContain "Ooopsie" // Exception class is not printed in this case.
        output shouldNotContain "<<IGNORED>>" // Also, we don't handle the inner exception.
    }

    @Test
    fun `guard from significant recursion`() {
        // The test logger does not handle errors gracefully, which should trigger
        // the fallback error handling in `AbstractLogger`.
        val bad: Any = object : Any() {
            override fun toString(): String {
                logger.atInfo().log("recursion: %s", this)
                return "<unused>"
            }
        }
        val output = tapConsole {
            logger.atInfo().log("evil value: %s", bad)
        }
        backend.logged.shouldHaveSize(100) // Matches to `AbstractLogger.MAX_ALLOWED_DEPTH`.
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        output shouldContain "unbounded recursion in log statement"
        output shouldContain "com.google.common.flogger.AbstractLoggerSpec"
    }

    @Test
    fun `allow logging exceptions thrown by a backend`() {
        // A backend that triggers a logging exception.
        val backend = object : MemoizingBackend() {
            override fun log(data: LogData) = throw LoggingException("allowed")
        }
        val logger = TestLogger.create(backend)
        val output = tapConsole {
            shouldThrow<LoggingException> {
                logger.atInfo().log("doomed to fail")
            }
        }
        backend.logged.shouldBeEmpty()
        output.shouldBeEmpty()
    }

    @Test
    fun `allow logging errors thrown by a backend`() {
        // A backend that triggers an `Error`.
        val backend: MemoizingBackend = object : MemoizingBackend() {
            override fun log(data: LogData) = throw Error("allowed")
        }
        val logger = TestLogger.create(backend)
        val output = tapConsole {
            shouldThrow<Error> {
                logger.atInfo().log("doomed to fail")
            }
        }
        backend.logged.shouldBeEmpty()
        output.shouldBeEmpty()
    }
}
