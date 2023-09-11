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

package com.google.common.flogger.backend.system

import com.google.common.flogger.backend.system.given.TestBackend
import com.google.common.flogger.backend.system.given.TestLogger
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.logging.Level
import java.util.logging.LogRecord
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`AbstractBackend` should")
internal class AbstractBackendSpec {

    private val logger = TestLogger("unused", Level.INFO)
    private val backend = TestBackend(logger)

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
            backend.log(record, forced) // Unforced logging.
            logger.logged shouldBe message
            logger.published shouldBe message
            backend.wasForcingLoggerUsed.shouldBeFalse()
        }

        @Test
        fun `not log at a disabled level`() {
            val message = "Disabled and Unforced"
            val record = LogRecord(Level.FINE, message)
            backend.log(record, forced) // Unforced logging.
            logger.logged shouldBe message
            logger.published.shouldBeNull()
            backend.wasForcingLoggerUsed.shouldBeFalse()
        }
    }

    @Nested inner class
    `when forced` {

        private val forced = true

        @Test
        fun `log at an enabled level using the underlying logger`() {
            // Forced and unforced logging is treated the same
            // if the used level is enabled.
            val message = "Enabled and Forced"
            val record = LogRecord(Level.INFO, message)
            backend.log(record, forced)
            logger.logged shouldBe message
            logger.published shouldBe message
            backend.wasForcingLoggerUsed.shouldBeFalse()
        }

        @Test
        fun `log at a disabled level using a special forcing logger`() {
            val message = "Disabled and Forced"
            val record = LogRecord(Level.FINE, message)
            backend.log(record, forced)
            logger.logged.shouldBe(null)
            logger.published.shouldBe(message)
            backend.wasForcingLoggerUsed.shouldBeTrue()
        }
    }
}
