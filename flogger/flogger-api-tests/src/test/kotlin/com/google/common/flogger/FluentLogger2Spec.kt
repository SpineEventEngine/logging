/*
 * Copyright (C) 2014 The Flogger Authors.
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

import com.google.common.flogger.FluentLogger2.forEnclosingClass
import com.google.common.flogger.testing.FakeLoggerBackend
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import java.util.logging.Level
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Fluent loggers are typically very simple classes whose only real
 * responsibility is to be a factory for specific API implementations.
 *
 * As such it needs very few tests itself.
 *
 * See [LogContextSpec] for the most tests related to base logging behavior.
 */
@DisplayName("`FluentLogger2` should")
internal class FluentLogger2Spec {

    @Test
    fun `create a logger for enclosing class`() {
        val logger = forEnclosingClass()
        val enclosingClass = this::class.java.name
        logger.name shouldBe enclosingClass

        // Note that this one-to-one binding of loggers and backends is not
        // strictly necessary, and in the future it is plausible that a configured
        // backend factory might return backends shared with many loggers.
        // In that situation, the logger name must still be the enclosing class name
        // (held separately by the logger itself) while the backend name could differ.
        val backend = logger.backend
        backend.loggerName shouldBe enclosingClass
    }

    @Test
    fun `provide 'no op' API for disabled levels`() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        backend.setLevel(Level.INFO)

        // Down to and including the configured log level are not no-op instances.
        logger.atSevere().shouldNotBeInstanceOf<LoggingApi.NoOp<*>>()
        logger.atWarning().shouldNotBeInstanceOf<LoggingApi.NoOp<*>>()
        logger.atInfo().shouldNotBeInstanceOf<LoggingApi.NoOp<*>>()

        logger.atSevere().shouldBeInstanceOf<FluentLogger2.Context>()
        logger.atWarning().shouldBeInstanceOf<FluentLogger2.Context>()
        logger.atInfo().shouldBeInstanceOf<FluentLogger2.Context>()

        // Below the configured log level you only get no-op instances.
        logger.atFine().shouldBeInstanceOf<LoggingApi.NoOp<*>>()
        logger.atFiner().shouldBeInstanceOf<LoggingApi.NoOp<*>>()
        logger.atFinest().shouldBeInstanceOf<LoggingApi.NoOp<*>>()

        // Just verify that logs below the current log level are discarded.
        logger.atFine().log("DISCARDED")
        logger.atFiner().log("DISCARDED")
        logger.atFinest().log("DISCARDED")
        backend.loggedCount shouldBe 0

        // But those at or above are passed to the backend.
        logger.atInfo().log("LOGGED")
        backend.loggedCount shouldBe 1
        backend.setLevel(Level.OFF)
        logger.atSevere().shouldBeInstanceOf<LoggingApi.NoOp<*>>()
        backend.setLevel(Level.ALL)
        logger.atFinest().shouldNotBeInstanceOf<LoggingApi.NoOp<*>>()
    }
}
