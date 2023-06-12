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

package io.spine.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.spine.logging.given.domain.AnnotatedClass
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`JvmLogger` should")
internal class JvmLoggerSpec {

    private val logger = LoggingFactory.loggerFor(this::class)

    @Test
    fun `create no-op instance when logging level is too low`() {
        // The default level (with Java logging as the backend) is `INFO`.
        (logger.atTrace() is LoggingApi.NoOp) shouldBe true
        (logger.atDebug() is LoggingApi.NoOp) shouldBe true

        (logger.atInfo() is LoggingApi.NoOp) shouldBe false
        (logger.atWarning() is LoggingApi.NoOp) shouldBe false
        (logger.atError() is LoggingApi.NoOp) shouldBe false
    }

    @Test
    fun `produce the output with the name of the logging class and calling method`() {
        val expectedMsg = "CLASS AND METHOD REFERENCE TEST"
        val consoleCheck = "*** Confirm console output ***"
        val consoleOutput = tapConsole {
            logger.atInfo().log { expectedMsg }
            System.err.println(consoleCheck)
        }
        consoleOutput shouldContain consoleCheck
        val expectedMethodReference = "produce the output with the name of" +
                " the logging class and calling method"
        consoleOutput shouldContain this::class.java.name
        consoleOutput shouldContain expectedMsg
        consoleOutput shouldContain expectedMethodReference
    }

    @Test
    fun `create an API with logging domain`() {
        val loggingDomain = LoggingFactory.loggingDomainOf(AnnotatedClass::class)
        loggingDomain.name shouldBe "OnClass"

        val msg = "Logging domain test"
        val consoleCheck = "*** Console output double-check ***"
        val loggerWithDomain = LoggingFactory.loggerFor(AnnotatedClass::class)
        val consoleOutput = tapConsole {
            loggerWithDomain.atInfo().log { msg }
            System.err.println(consoleCheck)
        }
        consoleOutput shouldContain consoleCheck
        consoleOutput shouldContain "[OnClass] $msg"
    }

    @Test
    fun `log from different classes`() {
        val consoleOutput = tapConsole {
            LoggingClass1().log()
            LoggingClass2().log()
        }
        consoleOutput shouldContain "Logging class 1"
        consoleOutput shouldContain "Logging class 2"
    }

    @Test
    fun `log once per N invocations`() {
        val invocations = 10
        val expectedMessage = "log once per N invocations test"
        val consoleOutput = tapConsole {
            repeat(invocations) {
                logger.atInfo()
                    .every(invocations)
                    .log { expectedMessage }
            }
        }
        consoleOutput shouldContainOnlyOnce expectedMessage
    }
}

private class LoggingClass1: WithLogging {
    fun log() = logger.atInfo().log { "Logging class 1" }
}

private class LoggingClass2: WithLogging {
    fun log() = logger.atInfo().log { "Logging class 2" }
}
