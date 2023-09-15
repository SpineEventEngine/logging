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

import com.google.common.flogger.LogContext
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.log4j2.given.MemoizingAppender
import com.google.common.flogger.parser.ParseException
import com.google.common.flogger.testing.FakeLogData
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.truth.Truth.assertThat
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName

@DisplayName("`Log4j2` should")
internal class Log4j2Spec {

    private val logger = createLogger()
    private val appender = MemoizingAppender()
    private val backend = Log4j2LoggerBackend(logger)
    private val logged = appender.events
    private val lastLogged
        get() = logged.last()

    companion object {

        /**
         * [INT_KEY] uses `Int::class.javaObjectType` to make sure
         * we get `Integer` class on JVM.
         *
         * Otherwise, Kotlin compiler passes `int` class for primitives.
         * It is important because metadata objects are generified,
         * which means they would use boxed primitives.
         */
        private val INT_KEY = MetadataKey.repeated("int", Int::class.javaObjectType)
        private val STR_KEY = MetadataKey.single("str", String::class.java)
        private const val LITERAL = "Hello world"
        private val DEFAULT_LEVEL = Level.INFO
    }

    @BeforeEach
    fun setUp() {
        resetLoggingConfig()
        logger.apply {
            level = Level.TRACE
            addAppender(appender)
        }
    }

    @AfterEach
    fun tearDown() {
        logger.removeAppender(appender)
        appender.stop()
    }

    // -------- Test helper methods --------
    private fun getMessage(index: Int): String {
        return logged[index].message.formattedMessage
    }

    private fun assertLogCount(count: Int) {
        assertThat(logged).hasSize(count)
    }

    private fun assertLogEntry(index: Int, message: String, level: Level) {
        val event = logged[index]
        assertThat(event.loggerName).isEqualTo(logger.name)
        assertThat(event.level).isEqualTo(level)
        assertThat(event.message.formattedMessage).isEqualTo(message)
        assertThat(event.thrown).isNull()
    }

    private fun assertLogSite(
        index: Int,
        className: String?,
        methodName: String?,
        line: Int,
        file: String?
    ) {
        val event = logged[index]
        val source = event.source
        assertThat(source.className).isEqualTo(className)
        assertThat(source.methodName).isEqualTo(methodName)
        assertThat(source.fileName).isEqualTo(file)
        assertThat(source.lineNumber).isEqualTo(line)
    }

    private fun assertThrown(index: Int, thrown: Throwable?) {
        assertThat(logged[index].thrown).isSameInstanceAs(thrown)
    }

    // -------- Unit tests start here (largely copied from the log4j tests) --------

    @Test
    fun `log messages`() {
        val (pattern, arguments) = "Hello %s %s" to arrayOf("Foo", "Bar")
        val printfData = FakeLogData.withPrintfStyle(pattern, *arguments)
        val literalData = FakeLogData.of(LITERAL)

        backend.log(printfData)
        backend.log(literalData)

        logged shouldHaveSize 2
        logged[0].formatted shouldBe pattern.format(*arguments)
        logged[1].formatted shouldBe LITERAL
    }

    @Test
    fun `handle the given metadata`() {
        val intValue = 23
        val strValue = "str value"
        val (pattern, argument) = "Foo='%s'" to "bar"
        val data = FakeLogData.withPrintfStyle(pattern, argument)
            .addMetadata(INT_KEY, intValue)
            .addMetadata(STR_KEY, strValue)

        backend.log(data)

        val expectedMessage = pattern.format(argument)
        val expectedContext = "int=$intValue str=\"$strValue\""
        logged shouldHaveSize 1
        lastLogged.level shouldBe DEFAULT_LEVEL
        lastLogged.formatted shouldBe "$expectedMessage [CONTEXT $expectedContext ]"
    }

    @Test
    fun testLevels() {
        backend.log(FakeLogData.of("finest").setLevel(java.util.logging.Level.FINEST))
        backend.log(FakeLogData.of("finer").setLevel(java.util.logging.Level.FINER))
        backend.log(FakeLogData.of("fine").setLevel(java.util.logging.Level.FINE))
        backend.log(FakeLogData.of("config").setLevel(java.util.logging.Level.CONFIG))
        backend.log(FakeLogData.of("info").setLevel(java.util.logging.Level.INFO))
        backend.log(FakeLogData.of("warning").setLevel(java.util.logging.Level.WARNING))
        backend.log(FakeLogData.of("severe").setLevel(java.util.logging.Level.SEVERE))
        assertLogCount(7)
        assertLogEntry(0, "finest", Level.TRACE)
        assertLogEntry(1, "finer", Level.TRACE)
        assertLogEntry(2, "fine", Level.DEBUG)
        assertLogEntry(3, "config", Level.DEBUG)
        assertLogEntry(4, "info", Level.INFO)
        assertLogEntry(5, "warning", Level.WARN)
        assertLogEntry(6, "severe", Level.ERROR)
    }

    @Test
    fun testSource() {
        backend.log(
            FakeLogData.of("First")
                .setLogSite(FakeLogSite.create("<class>", "<method>", 42, "<file>"))
        )
        backend.log(
            FakeLogData.of("No file")
                .setLogSite(FakeLogSite.create("<class>", "<method>", 42, null))
        )
        backend.log(
            FakeLogData.of("No line")
                .setLogSite(FakeLogSite.create("<class>", "<method>", -1, null))
        )
        assertLogCount(3)
        assertLogSite(0, "<class>", "<method>", 42, "<file>")
        assertLogSite(1, "<class>", "<method>", 42, null)
        assertLogSite(2, "<class>", "<method>", -1, null)
    }

    @Test
    fun testErrorHandling() {
        val data: LogData = FakeLogData.withPrintfStyle("Hello %?X World", "ignored")
        try {
            backend.log(data)
            Assertions.fail("expected ParseException")
        } catch (expected: ParseException) {
            assertLogCount(0)
            backend.handleError(expected, data)
            assertLogCount(1)
            assertThat(getMessage(0)).contains("lo %[?]X Wo")
        }
    }

    @Test
    fun testWithThrown() {
        val cause = Throwable("Original Cause")
        backend.log(FakeLogData.of("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause))
        assertLogCount(1)
        assertThrown(0, cause)
    }
}

/**
 * Resets the logger configuration to prevent a clash
 * with [Log4j2ScopedLoggingSpec].
 */
private fun resetLoggingConfig() {
    val classloaderSpecific = false
    val context = LoggerContext.getContext(classloaderSpecific)
    context.apply {
        configuration = DefaultConfiguration()
        updateLoggers()
    }
}

private val serialNumbers = AtomicInteger()

/**
 * Creates a logger with a unique name.
 *
 * A unique name should produce a different logger for each test,
 * allowing tests to be run in parallel.
 */
private fun createLogger(): Logger {
    val suiteName = Log4j2Test::class.simpleName!!
    val testSerial = serialNumbers.incrementAndGet()
    val loggerName = "%s_%02d".format(suiteName, testSerial)
    val logger = LogManager.getLogger(loggerName) as Logger
    return logger
}

/**
 * Returns a formatted message from this [LogEvent].
 */
private val LogEvent.formatted
    get() = message.formattedMessage
