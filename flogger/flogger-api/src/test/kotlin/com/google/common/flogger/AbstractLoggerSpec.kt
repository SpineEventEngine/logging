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
import io.spine.logging.testing.tapConsole
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
         * The same as in [AbstractLogger.reportError] method.
         */
        private const val LOGGING_ERROR = "logging error"

        /**
         * Matches ISO 8601 date/time format.
         *
         * [DOT_MATCHES_ALL] option makes `.*` match line terminators as well.
         *
         * @see <a href="https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html">SimpleDateFormat</a>
         */
        private val TIMESTAMP_PREFIX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[-+]\\d{4}: .*"
                .toRegex(DOT_MATCHES_ALL)
    }

    @Test
    fun `report exceptions`() {
        val exception = IllegalStateException("Ooopsie")
        val bad: Any = ThrowingAny(exception)

        val output = tapConsole {
            logger.atInfo().log("Bad value: %s.", bad)
        }

        backend.logged.shouldBeEmpty()
        output shouldMatch TIMESTAMP_PREFIX
        output shouldContain LOGGING_ERROR
        output shouldContain "${exception.stackTrace[0]}"
        output shouldContain "${exception::class.simpleName}: ${exception.message}"
    }

    /**
     * Tests a worst case scenario whereby object's `toString()` method throws
     * an exception, which itself throws an exception on `toString()` call.
     */
    @Test
    fun `report nested exceptions`() {
        val innerException = IllegalStateException("<<IGNORED>>")
        val outerException = object : IllegalStateException("Ooopsie") {
            private val serialVersionUID: Long = 42L
            override fun toString(): String = throw innerException // It is a nested throwing.
        }
        val bad: Any = ThrowingAny(outerException)

        val output = tapConsole {
            logger.atInfo().log("Bad value: %s.", bad)
        }

        backend.logged.shouldBeEmpty()
        output shouldMatch TIMESTAMP_PREFIX
        output shouldContain LOGGING_ERROR

        // Exception class is not printed in this case.
        output shouldContain outerException.message!!
        // Also, the inner exception is not handled.
        output shouldNotContain innerException.message!!
    }

    @Test
    fun `guard from significant recursion`() {
        val bad: Any = object : Any() {
            override fun toString(): String {
                logger.atInfo().log("recursion: %s", this)
                return "<unused>"
            }
        }

        val output = tapConsole {
            logger.atInfo().log("Evil value: %s.", bad)
        }

        // Defined by `AbstractLogger.MAX_ALLOWED_RECURSION_DEPTH` constant.
        backend.logged.shouldHaveSize(100)

        output shouldMatch TIMESTAMP_PREFIX
        output shouldContain LOGGING_ERROR
        output shouldContain this::class.simpleName!!
        output shouldContain "unbounded recursion in log statement"
    }

    @Test
    fun `allow logging exceptions thrown by a backend`() {
        val backend = object : MemoizingBackend() {
            override fun log(data: LogData) = throw LoggingException("allowed")
        }
        val logger = TestLogger.create(backend)

        val output = tapConsole {
            shouldThrow<LoggingException> {
                logger.atInfo().log()
            }
        }

        backend.logged.shouldBeEmpty()
        output.shouldBeEmpty()
    }

    @Test
    @Suppress("TooGenericExceptionThrown") // Plain `Error` is OK for tests.
    fun `allow logging errors thrown by a backend`() {
        val backend: MemoizingBackend = object : MemoizingBackend() {
            override fun log(data: LogData) = throw Error("allowed")
        }
        val logger = TestLogger.create(backend)

        val output = tapConsole {
            shouldThrow<Error> {
                logger.atInfo().log()
            }
        }

        backend.logged.shouldBeEmpty()
        output.shouldBeEmpty()
    }
}

/**
 * An [Any] that throws the given [exception] on call to [toString].
 */
@Suppress("ExceptionRaisedInUnexpectedLocation") // Intentional throwing in `toString()`.
private class ThrowingAny(private val exception: Exception) {
    override fun toString(): String = throw exception
}
