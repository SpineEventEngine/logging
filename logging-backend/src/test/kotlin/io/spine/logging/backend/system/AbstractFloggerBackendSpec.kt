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

package io.spine.logging.backend.system

import io.spine.logging.backend.system.given.MemoizingBackend
import io.spine.logging.backend.system.given.MemoizingLogger
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.logging.Level
import java.util.logging.LogRecord
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [AbstractFloggerBackend].
 *
 * @see <a href="https://github.com/google/flogger/blob/70c5aea863952ee61b3d33afb41f2841b6d63455/api/src/test/java/com/google/common/flogger/backend/system/AbstractBackendTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`AbstractBackend` should")
internal class AbstractFloggerBackendSpec {

    private val logger = MemoizingLogger("unused", Level.INFO)
    private val backend = MemoizingBackend(logger)

    @Test
    fun `say whether logging is enabled for the given level`() {
        backend.isLoggable(Level.INFO).shouldBeTrue()
        backend.isLoggable(Level.FINE).shouldBeFalse()
    }

    @Nested inner class
    `when unforced` {

        private val forced = false

        @Test
        fun `log at an enabled level using the underlying logger`() {
            val message = "Enabled and Unforced"
            val record = LogRecord(Level.INFO, message)
            backend.log(record, forced)
            logger.captured shouldBe message
            logger.published shouldBe message
            backend.wasForcingLoggerUsed.shouldBeFalse()
        }

        @Test
        fun `not log at a disabled level`() {
            val message = "Disabled and Unforced"
            val record = LogRecord(Level.FINE, message)
            backend.log(record, forced)
            logger.captured shouldBe message // Passed to the logger.
            logger.published.shouldBeNull() // But not published.
            backend.wasForcingLoggerUsed.shouldBeFalse()
        }
    }

    @Nested inner class
    `when forced` {

        private val forced = true

        @Test
        fun `log at an enabled level using the underlying logger`() {
            // Forced and unforced logging is treated the same way
            // if the used level is enabled.
            val message = "Enabled and Forced"
            val record = LogRecord(Level.INFO, message)
            backend.log(record, forced)
            logger.captured shouldBe message
            logger.published shouldBe message
            backend.wasForcingLoggerUsed.shouldBeFalse()
        }

        /**
         * Tests how the abstract backend pushes a forced log statement
         * through a disabled level.
         *
         * To achieve this, it creates another logger without any
         * level restrictions:
         *
         * ```
         * val forcingLogger = crateOrGetForcingLogger(underlyingLogger)
         * forcingLogger.setLevel(Level.ALL)
         * forcingLogger.log(...)
         * ```
         *
         * The underlying logger will not receive a call to `Logger.log()`.
         * This method will be called upon a forcing logger. But when a forcing
         * logger calls log handlers, the parental handlers are also called.
         */
        @Test
        fun `log at a disabled level using a special forcing logger`() {
            val message = "Disabled and Forced"
            val record = LogRecord(Level.FINE, message)
            backend.log(record, forced)
            logger.captured.shouldBeNull()
            logger.published shouldBe message
            backend.wasForcingLoggerUsed.shouldBeTrue()
        }
    }
}
