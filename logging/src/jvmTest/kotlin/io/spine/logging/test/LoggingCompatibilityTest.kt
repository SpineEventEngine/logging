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

import io.spine.logging.LoggingFactory
import io.spine.logging.log
import io.spine.logging.logVarargs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests to verify that the logging API works correctly after removing formatting infrastructure.
 */
@DisplayName("Logging API should")
internal class LoggingCompatibilityTest {

    private val logger = LoggingFactory.loggerFor(this::class)

    @Test
    @DisplayName("support lazy evaluation with lambda")
    fun testLazyEvaluation() {
        logger.atInfo().log { "Lazy evaluation message" }
    }

    @Test
    @DisplayName("support extension function with string literal")
    fun testStringLiteral() {
        logger.atInfo().log("String literal message")
    }

    @Test
    @DisplayName("support extension function with formatted string and single argument")
    fun testFormattedMessageOneArg() {
        logger.atInfo().log("Formatted message: %s", "test")
    }

    @Test
    @DisplayName("support extension function with formatted string and multiple arguments")
    fun testFormattedMessageMultipleArgs() {
        logger.atInfo().log("Multiple args: %s, %d, %s", "first", 42, "third")
    }

    @Test
    @DisplayName("support extension function with varargs")
    fun testVarargs() {
        logger.atInfo().log("Varargs: %s %s %s", "one", "two", "three")
    }

    @Test
    @DisplayName("support logVarargs extension function")
    fun testLogVarargs() {
        val args = arrayOf<Any?>("arg1", "arg2")
        logger.atInfo().logVarargs("Message with array: %s %s", args)
    }

    @Test
    @DisplayName("support fluent API chaining")
    fun testFluentChaining() {
        logger.atInfo()
            .every(100)
            .log { "Fluent chaining with lazy evaluation" }
    }

    @Test
    @DisplayName("support empty log statement")
    fun testEmptyLog() {
        logger.atInfo().log()
    }
}
