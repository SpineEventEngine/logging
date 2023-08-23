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
 * Tests for [AbstractLogger].
 *
 * An abstract logger is a factory for instances of fluent logging APIs,
 * used to build log statements via method chaining.
 *
 * See [LogContextSpec] for the most tests related to base logging behavior.
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/AbstractLoggerTest.java">
 *     Original Java code of Google Flogger</a>
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
    fun `report runtime exceptions`() {
        val bad: Any = object : Any() {
            override fun toString(): String = error("Ooopsie")
        }
        val output = tapConsole {
            logger.atInfo().log("bad value: %s", bad)
        }
        backend.logged.shouldBeEmpty()
        output shouldMatch ISO_TIMESTAMP_PREFIX
        output shouldContain "logging error"
        output shouldContain "com.google.common.flogger.AbstractLoggerSpec.report an error"
        output shouldContain "java.lang.IllegalStateException: Ooopsie"
    }

    @Test
    fun `report nested runtime exceptions`() {
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
    @Suppress("TooGenericExceptionThrown") // Just `Error` is OK for tests.
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
