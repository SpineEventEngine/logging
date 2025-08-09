/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.test

import io.kotest.matchers.string.shouldContain
import io.spine.logging.LoggingFactory
import io.spine.logging.log
import io.spine.logging.logVarargs
import io.spine.logging.testing.tapConsole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests to verify that the logging API works correctly after removing formatting infrastructure.
 *
 * This test suite merely ensures that there are these functions.
 */
@Suppress("DEPRECATION")
@DisplayName("Logging API should provide extension functions for")
internal class LoggingCompatibilityTest {

    private val logger = LoggingFactory.loggerFor(this::class)

    @Test
    fun `string literal`() {
        logger.atInfo().log("String literal message")
    }

    @Test
    fun `formatted string and single argument`() {
        val output = tapConsole {
            logger.atInfo().log("Formatted message: %s", "test")
        }
        output shouldContain "Formatted message: test"
    }

    @Test
    fun `formatted string and multiple arguments`() {
        val output = tapConsole {
            logger.atInfo().log("Multiple args: %s, %d, %s", "first", 42, "third")
        }
        output shouldContain "Multiple args: first, 42, third"
    }

    @Test
    fun `provide 'logVarargs' extension function`() {
        val output = tapConsole {
            val args = arrayOf<Any?>("arg1", "arg2")
            logger.atInfo().logVarargs("Message with array: %s %s", args)
        }
        output shouldContain "Message with array: arg1 arg2"
    }
}
