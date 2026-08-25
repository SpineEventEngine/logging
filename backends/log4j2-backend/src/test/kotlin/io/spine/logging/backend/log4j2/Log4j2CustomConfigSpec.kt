/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.logging.backend.log4j2

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.spine.logging.LogContext.Key
import io.spine.logging.MetadataKey
import io.spine.logging.backend.log4j2.given.MemoizingAppender
import io.spine.logging.backend.log4j2.given.StubLogData
import io.spine.logging.backend.log4j2.given.createLogger
import io.spine.logging.backend.log4j2.given.formatted
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated

/**
 * Tests for [Log4j2LoggerBackend] running under a custom Log4j2 configuration.
 *
 * Unlike [Log4j2LoggerBackendSpec], which runs under [DefaultConfiguration],
 * these tests install a programmatically built configuration first, taking
 * the code path used when an application provides its own `log4j2.xml`.
 *
 * With a custom configuration, the log message must not carry
 * the `[CONTEXT ... ]` suffix: the metadata goes to the context data map
 * of the log event, where a user-defined layout can render it, e.g., via `%X`.
 */
@Isolated // Mutates the Log4j2 configuration, which is a process-global state.
@DisplayName("Under a custom Log4j2 configuration, `Log4j2LoggerBackend` should")
internal class Log4j2CustomConfigSpec {

    private lateinit var backend: Log4j2LoggerBackend
    private lateinit var logged: List<LogEvent>
    private val lastLogged get() = logged.last()

    companion object {
        private val INT_KEY = MetadataKey.repeated<Int>("int")
        private val STR_KEY = MetadataKey.single<String>("str")
        private const val LITERAL = "Hello world"
    }

    @BeforeEach
    fun installCustomConfiguration() {
        val config = ConfigurationBuilderFactory.newConfigurationBuilder()
            .setConfigurationName(Log4j2CustomConfigSpec::class.java.simpleName)
            .build()
        Configurator.reconfigure(config)
        val appender = MemoizingAppender()
        val logger = createLogger(Log4j2CustomConfigSpec::class, appender)
        backend = Log4j2LoggerBackend(logger)
        logged = appender.events
    }

    /**
     * Returns Log4j2 to the automatically resolved configuration.
     *
     * Since the test classpath contains no configuration file, this brings
     * [DefaultConfiguration] back, as expected by the sibling specs.
     */
    @AfterEach
    fun restoreDefaultConfiguration() {
        Configurator.reconfigure()
    }

    @Test
    fun `run against a non-default configuration`() {
        val config = LoggerContext.getContext(false).configuration
        config.shouldNotBeInstanceOf<DefaultConfiguration>()
    }

    @Test
    fun `log a literal message`() {
        val literalData = StubLogData(LITERAL)
        backend.log(literalData)
        lastLogged.formatted shouldBe LITERAL
    }

    @Test
    fun `not append metadata to the message`() {
        val message = "Foo='bar'"
        val data = StubLogData(message)
            .addMetadata(INT_KEY, 23)
            .addMetadata(STR_KEY, "str value")
        backend.log(data)
        lastLogged.formatted shouldBe message
    }

    @Test
    fun `pass metadata via the context data map`() {
        val intValue = 23
        val strValue = "str value"
        val data = StubLogData(LITERAL)
            .addMetadata(INT_KEY, intValue)
            .addMetadata(STR_KEY, strValue)
        backend.log(data)
        lastLogged.contextData.toMap() shouldBe mapOf(
            INT_KEY.label to "$intValue",
            STR_KEY.label to strValue
        )
    }

    @Test
    fun `pass the cause of the log statement`() {
        val cause = Throwable("Original Cause")
        val data = StubLogData(LITERAL)
            .addMetadata(Key.LOG_CAUSE, cause)
        backend.log(data)
        lastLogged.thrown shouldBeSameInstanceAs cause
        lastLogged.formatted shouldBe LITERAL
        // The `cause` also lands in the context data map, mirroring
        // the upstream Flogger behavior.
        lastLogged.contextData.toMap() shouldBe mapOf(Key.LOG_CAUSE.label to "$cause")
    }
}
