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
import java.lang.Thread.sleep
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.DurationUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`JvmLogger` should")
internal class JvmLoggerSpec {

    private val logger = LoggingFactory.loggerFor(this::class)
    private val message = "logging test message"

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

    @Nested
    internal inner class `, when given an invocations-limiting logging rate,` {

        @Test
        fun `log on the first invocation`() {
            val invocations = 3
            val loggingRate = invocations
            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .every(loggingRate)
                        .log { message }
                }
            }
            consoleOutput shouldContainOnlyOnce message
        }

        @Test
        fun `use the latest specified rate`() {
            val invocations = 10
            val initialLoggingRate = 3
            val finalLoggingRate = 5
            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .every(initialLoggingRate)
                        .every(finalLoggingRate)
                        .log { message }
                }
            }
            val expectedTimesLogged = expectedExecutions(invocations, finalLoggingRate)
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe expectedTimesLogged
        }

        @Test
        fun `throw on zero rate`() {
            val loggingRate = 0
            assertThrows<IllegalArgumentException> {
                logger.atInfo()
                    .every(loggingRate)
            }
        }

        @Test
        fun `throw on negative rates`() {
            for (loggingRate in -1 downTo -5) {
                assertThrows<IllegalArgumentException> {
                    logger.atInfo()
                        .every(loggingRate)
                }
            }
        }

        @Test
        fun `log no more often than the rate allows`() {
            val loggingRate = 5
            val invocations = 28

            val numberedMessage = { i: Int -> "log message #$i." }
            val consoleOutput = tapConsole {
                (1..invocations).forEach { i ->
                    logger.atInfo()
                        .every(loggingRate)
                        .log { numberedMessage(i) }
                }
            }

            val expectedLoggedInvocations = (1..invocations step loggingRate).toList()
            expectedLoggedInvocations.forEach { i ->
                consoleOutput shouldContainOnlyOnce numberedMessage(i)
            }

            val expectedLoggedTimes = expectedLoggedInvocations.size
            val timesLogged = consoleOutput.lines().size / 2 // Logger prints two lines per a message.
            timesLogged shouldBe expectedLoggedTimes
        }
    }

    @Nested
    internal inner class `, when given a time-limiting logging rate, ` {

        @Test
        fun `log on the first invocation`() {
            val seconds = 5
            val consoleOutput = tapConsole {
                logger.atInfo()
                    .atMostEvery(seconds, SECONDS)
                    .log { message }
            }
            consoleOutput shouldContainOnlyOnce message
        }

        @Test
        fun `use the latest specified interval`() {
            val initialInterval = 100
            val finalInterval = 500
            val invocations = 10

            val consoleOutput = tapConsole {
                repeat(invocations, initialInterval) {
                    logger.atInfo()
                        .atMostEvery(initialInterval, MILLISECONDS)
                        .atMostEvery(finalInterval, MILLISECONDS)
                        .log { message }
                }
            }

            val expectedTimesLogged = invocations * initialInterval / finalInterval
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe expectedTimesLogged
        }

        @Test
        fun `throw on negative durations`() {
            for (interval in -1 downTo -1000 step 100) {
                assertThrows<IllegalArgumentException> {
                    logger.atInfo()
                        .atMostEvery(interval, MILLISECONDS)
                }
            }
        }

        @Test
        fun `have no effect on a zero duration`() {
            val invocations = 10
            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .atMostEvery(0, MILLISECONDS)
                        .log { message }
                }
            }
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe invocations
        }

        @Test
        fun `log no more often than the rate allows`() {
            val invocationInterval = 120
            val rateInterval = 300
            val invocations = 10

            val timestampedMessage = { millis: Int -> "log message $millis ms." }
            val totalDuration = invocations * invocationInterval
            val consoleOutput = tapConsole {
                for (millis in 0..totalDuration step invocationInterval) {
                    logger.atInfo()
                        .atMostEvery(rateInterval, MILLISECONDS)
                        .log { timestampedMessage(millis) }
                    sleep(invocationInterval.toLong())
                }
            }

            val expectedLoggedTimestamps = expectedLoggedTimestamps(
                invocations,
                invocationInterval,
                rateInterval
            )
            expectedLoggedTimestamps.forEach { i ->
                consoleOutput shouldContainOnlyOnce timestampedMessage(i)
            }

            val expectedLoggedTimes = expectedLoggedTimestamps.size
            val timesLogged = consoleOutput.lines().size / 2 // Logger prints two lines per a message.
            timesLogged shouldBe expectedLoggedTimes
        }
    }

    @Nested
    internal inner class `, when given multiple rate limiters` {

        @Test
        fun `limit rather by invocations`() {

        }

        @Test
        fun `limit rather by duration`() {

        }

        @Test
        fun `limit equally by invocations and duration`() {

        }
    }
}

private class LoggingClass1: WithLogging {
    fun log() = logger.atInfo().log { "Logging class 1" }
}

private class LoggingClass2: WithLogging {
    fun log() = logger.atInfo().log { "Logging class 2" }
}

private fun String.occurrencesOf(substring: String) = split(substring).size - 1

/**
 * Calculates how many times a logging statement will actually be executed
 * when it is execution rate is [restricted][LoggingApi.every].
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
private fun expectedExecutions(invocations: Int, rate: Int): Int {
    val sureExecutions = invocations / rate
    val hasRemainder = invocations % rate != 0
    return sureExecutions + if (hasRemainder) 1 else 0
}

@Suppress("SameParameterValue") // Extracted to a method for better readability.
private fun repeat(times: Int, intervalMillis: Int, action: () -> Unit) {
    repeat(times - 1) {
        action()
        sleep(intervalMillis.toLong())
    }
    action()
}

@Suppress("SameParameterValue") // Extracted to a method for better readability.
private fun expectedLoggedTimestamps(
    invocations: Int,
    invocationInterval: Int,
    rateInterval: Int,
): List<Int> {

    val result = mutableListOf<Int>()
    var currentMillis = -invocationInterval
    var lastLogged = -rateInterval

    repeat(invocations) {
        currentMillis += invocationInterval
        if (currentMillis >= lastLogged + rateInterval) {
            result.add(currentMillis)
            lastLogged = currentMillis
        }
    }

    return result
}
