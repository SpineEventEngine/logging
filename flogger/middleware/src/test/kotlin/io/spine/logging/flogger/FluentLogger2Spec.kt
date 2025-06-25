/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.flogger

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.spine.logging.flogger.FluentLogger2.forEnclosingClass
import io.spine.logging.flogger.backend.given.MemoizingLoggerBackend
import java.util.logging.Level
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [FluentLogger2].
 *
 * Fluent loggers are typically very simple classes whose only real
 * responsibility is to be a factory for specific API implementations.
 *
 * As such it needs very few tests itself.
 *
 * See [LogContextSpec] for the most tests related to base logging behavior.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/FluentLoggerTest.java">
 *     Original Java code of Google Flogger</a>
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
    fun `provide a no-op API for disabled levels`() {
        val backend = MemoizingLoggerBackend()
        val logger = FluentLogger2(backend)
        backend.setLevel(Level.INFO)

        // Down to and including the configured log level are not no-op instances.
        logger.atSevere().shouldNotBeInstanceOf<FloggerApi.NoOp<*>>()
        logger.atWarning().shouldNotBeInstanceOf<FloggerApi.NoOp<*>>()
        logger.atInfo().shouldNotBeInstanceOf<FloggerApi.NoOp<*>>()

        logger.atSevere().shouldBeInstanceOf<FluentLogger2.Context>()
        logger.atWarning().shouldBeInstanceOf<FluentLogger2.Context>()
        logger.atInfo().shouldBeInstanceOf<FluentLogger2.Context>()

        // Below the configured log level, you only get no-op instances.
        logger.atFine().shouldBeInstanceOf<FloggerApi.NoOp<*>>()
        logger.atFiner().shouldBeInstanceOf<FloggerApi.NoOp<*>>()
        logger.atFinest().shouldBeInstanceOf<FloggerApi.NoOp<*>>()

        // Just verify that logs below the current log level are discarded.
        logger.atFine().log("DISCARDED")
        logger.atFiner().log("DISCARDED")
        logger.atFinest().log("DISCARDED")
        backend.loggedCount shouldBe 0

        // But those at or above are passed to the backend.
        logger.atInfo().log("LOGGED")
        backend.loggedCount shouldBe 1
        backend.setLevel(Level.OFF)
        logger.atSevere().shouldBeInstanceOf<FloggerApi.NoOp<*>>()
        backend.setLevel(Level.ALL)
        logger.atFinest().shouldNotBeInstanceOf<FloggerApi.NoOp<*>>()
    }
}
