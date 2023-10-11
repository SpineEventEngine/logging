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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the most fundamental functionality of `spine-logging` library.
 *
 * This test suite is meant to be implemented in particular Spine modules
 * to verify the actual logging is happening in real conditions.
 *
 * To add this suite to an arbitrary module, create a new test that extends
 * from this class. No additional code is needed.
 *
 * For example:
 *
 * ```
 * class LoggingSmokeTest : AbstractLoggingSmokeTest()
 * ```
 *
 * Make sure, `spine-logging-smoke-test` library is on test classpath:
 *
 * ```
 * testImplementation(Spine.Logging.smokeTest)
 * ```
 *
 * ## Implementation details
 *
 * Tests assert the logged messages using [tapJavaLogging] util. This util writes
 * a stream of the logged messages into a plain [String], which then makes possible
 * to assert the result.
 *
 * The messages are captured by a custom Java Logging Handler. This approach is less
 * fair compared to interception of `out` and `err` streams, but a way simpler
 * and more reliable. Inserting of a “listening handler” into Java Logging makes
 * tests dependable on a specific backend, but eliminates difficulties with interception of
 * the default streams.
 */
@Suppress("FunctionNaming", "FunctionName")
@DisplayName("Spine Logging should")
public abstract class AbstractLoggingSmokeTest {

    private val message = "some logging text"

    @Test
    public fun `log by implementing 'WithLogging' interface`() {
        val loggingInstance = WithLoggingClass()
        val output = tapJavaLogging {
            loggingInstance.logSomething(message)
        }

        output shouldContain WithLoggingClass::class.qualifiedName!!
        output shouldContain Level.INFO.name
        output shouldContain message
    }

    @Test
    public fun `log using instance of 'JvmLogger'`() {
        val loggingClass = AbstractLoggingSmokeTest::class
        val logger = LoggingFactory.loggerFor(loggingClass).also {
            it::class shouldBe JvmLogger::class
        }
        val output = tapJavaLogging {
            logger.atInfo()
                .log { message }
        }

        output shouldContain loggingClass.qualifiedName!!
        output shouldContain Level.INFO.name
        output shouldContain message
    }
}

private class WithLoggingClass : WithLogging {
    fun logSomething(msg: String) {
        logger.atInfo().log { msg }
    }
}
