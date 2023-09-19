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

package com.google.common.flogger.backend.log4j2

import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.LogSite
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.backend.log4j2.given.MemoizingAppender
import com.google.common.flogger.parser.ParseException
import com.google.common.flogger.testing.FakeLogData
import com.google.common.flogger.testing.FakeLogSite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

// These aliases help to distinguish different levels without FQN.
private typealias JulLevel = java.util.logging.Level
private typealias Log4jLevel = org.apache.logging.log4j.Level

/**
 * Tests for [Log4j2LoggerBackend].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/log4j2/src/test/java/com/google/common/flogger/backend/log4j2/Log4j2Test.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`Log4j2LoggerBackendSpec` should")
internal class Log4j2LoggerBackendSpec {

    private lateinit var backend: Log4j2LoggerBackend
    private lateinit var logged: List<LogEvent>
    private val lastLogged get() = logged.last()

    companion object {

        /**
         * [INT_KEY] uses `Int::class.javaObjectType` to make sure we get
         * `Integer` class on JVM.
         *
         * Otherwise, Kotlin compiler passes `int` class for primitives.
         * It is important because metadata objects are generified,
         * which means they would use boxed primitives.
         */
        private val INT_KEY = MetadataKey.repeated("int", Int::class.javaObjectType)
        private val STR_KEY = MetadataKey.single("str", String::class.java)
        private const val LITERAL = "Hello world"
    }

    @BeforeEach
    fun setUp() {
        val memoizingAppender = MemoizingAppender()
        val logger = createLogger(memoizingAppender)
        backend = Log4j2LoggerBackend(logger)
        logged = memoizingAppender.events
    }

    @Test
    fun `log a literal message`() {
        val literalData = FakeLogData.of(LITERAL)
        backend.log(literalData)
        lastLogged.formatted shouldBe LITERAL
    }

    @Test
    fun `log a formatted message`() {
        val (pattern, arguments) = "Hello %s %s" to arrayOf("Foo", "Bar")
        val printfData = FakeLogData.withPrintfStyle(pattern, *arguments)
        backend.log(printfData)
        lastLogged.formatted shouldBe pattern.format(*arguments)
    }

    @Test
    fun `use the default level`() {
        val defaultLevel = Log4jLevel.INFO // By default Log4j settings.
        val data = FakeLogData.of(LITERAL)
        backend.log(data)
        lastLogged.level shouldBe defaultLevel
    }

    @Nested
    inner class
    `append metadata` {

        @Test
        fun `with custom keys`() {
            val intValue = 23
            val strValue = "str value"
            val (pattern, argument) = "Foo='%s'" to "bar"
            val data = FakeLogData.withPrintfStyle(pattern, argument)
                .addMetadata(INT_KEY, intValue)
                .addMetadata(STR_KEY, strValue)
            backend.log(data)
            val expectedMessage = pattern.format(argument)
            val expectedMetadata = "int=$intValue str=\"$strValue\""
            lastLogged.formatted shouldBe "$expectedMessage [CONTEXT $expectedMetadata ]"
        }

        @Test
        fun `with log cause`() {
            val cause = Throwable("Original Cause")
            val data = FakeLogData.of(LITERAL)
                .addMetadata(Key.LOG_CAUSE, cause)
            backend.log(data)
            lastLogged.thrown shouldBeSameInstanceAs cause
        }
    }

    @Test
    fun `match Java logging levels`() {
        val expectedMatches = mapOf(
            JulLevel.FINEST to Log4jLevel.TRACE,
            JulLevel.FINER to Log4jLevel.TRACE,
            JulLevel.FINE to Log4jLevel.DEBUG,
            JulLevel.CONFIG to Log4jLevel.DEBUG,
            JulLevel.INFO to Log4jLevel.INFO,
            JulLevel.WARNING to Log4jLevel.WARN,
            JulLevel.SEVERE to Log4jLevel.ERROR
        )
        expectedMatches.forEach { (julLevel, expectedLog4jLevel) ->
            val message = julLevel.name
            val data = FakeLogData.of(message)
                .setLevel(julLevel)
            backend.log(data)
            lastLogged.level shouldBe expectedLog4jLevel
            lastLogged.formatted shouldBe message
        }
    }

    @Nested
    inner class
    `append log site` {

        @Test
        fun `with full information`() {
            val logSite = FakeLogSite.create("<class>", "<method>", 42, "<file>")
            testLogSite(logSite)
        }

        @Test
        fun `without source file`() {
            val logSite = FakeLogSite.create("<class>", "<method>", 42, null)
            testLogSite(logSite)
        }

        @Test
        fun `without line number and source file`() {
            val logSite = FakeLogSite.create("<class>", "<method>", -1, null)
            testLogSite(logSite)
        }

        private fun testLogSite(logSite: LogSite) {
            val data = FakeLogData.of("")
                .setLogSite(logSite)
            backend.log(data)
            val actual = lastLogged.source
            with(actual) {
                className shouldBe logSite.className
                methodName shouldBe logSite.methodName
                lineNumber shouldBe logSite.lineNumber
                fileName shouldBe logSite.fileName
            }
        }
    }

    @Test
    fun `propagate parsing errors`() {
        val data = FakeLogData.withPrintfStyle("Hello %?X World", "ignored")
        val parseException = shouldThrow<ParseException> {
            backend.log(data)
        }
        logged.shouldBeEmpty()
        backend.handleError(parseException, data)
        logged shouldHaveSize 1
        println(lastLogged.formatted)
        lastLogged.formatted shouldContain "LOGGING ERROR: invalid flag"
    }
}

private val serialNumbers = AtomicInteger()

/**
 * Creates a logger with a unique name and the given [appender].
 *
 * The default console appender is removed.
 *
 * A unique name should produce a different logger for each test,
 * allowing tests to be run in parallel.
 */
private fun createLogger(appender: Appender): Logger {
    val suiteName = Log4j2LoggerBackendSpec::class.simpleName!!
    val testSerial = serialNumbers.incrementAndGet()
    val loggerName = "%s_%02d".format(suiteName, testSerial)
    val logger = LogManager.getLogger(loggerName) as Logger
    logger.apply {
        level = Log4jLevel.TRACE
        appenders.forEach { removeAppender(it.value) }
        addAppender(appender)
    }
    return logger
}

/**
 * Returns a formatted message from this [LogEvent].
 */
private val LogEvent.formatted
    get() = message.formattedMessage
