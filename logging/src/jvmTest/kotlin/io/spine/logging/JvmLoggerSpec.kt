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
    internal inner class `, when given an invocation-based rate limit,` {

        @Test
        fun `always log on the first invocation`() {
            val invocationRate = 5
            val consoleOutput = tapConsole {
                logger.atInfo()
                    .every(invocationRate)
                    .log { message }
            }
            consoleOutput shouldContainOnlyOnce message
        }

        @Test
        fun `use the latest specified rate`() {
            val invocations = 10
            val initialRate = 3
            val finalRate = 5

            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .every(initialRate)
                        .every(finalRate)
                        .log { message }
                }
            }

            val expectedTimesLogged = expectedExecutions(invocations, finalRate)
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe expectedTimesLogged
        }

        @Test
        fun `throw on zero rate`() {
            val invocationRate = 0
            assertThrows<IllegalArgumentException> {
                logger.atInfo()
                    .every(invocationRate)
            }
        }

        @Test
        fun `throw on negative rates`() {
            for (invocationRate in -1 downTo -5) {
                assertThrows<IllegalArgumentException> {
                    logger.atInfo()
                        .every(invocationRate)
                }
            }
        }

        @Test
        fun `log no more often than the rate allows`() {
            val invocations = 28
            val invocationRate = 5

            val numberedMessage = { i: Int -> "log message #$i." }
            val consoleOutput = tapConsole {
                (1..invocations).forEach { i ->
                    logger.atInfo()
                        .every(invocationRate)
                        .log { numberedMessage(i) }
                }
            }

            val expectedInvocations = (1..invocations step invocationRate).toList()
            expectedInvocations.forEach { i ->
                consoleOutput shouldContainOnlyOnce numberedMessage(i)
            }

            // The logger prints two lines per a message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedInvocations.size
        }
    }

    @Nested
    internal inner class `, when given a time interval-based rate limit,` {

        @Test
        fun `always log on the first invocation`() {
            val timeRateMillis = 100
            val consoleOutput = tapConsole {
                logger.atInfo()
                    .atMostEvery(timeRateMillis, MILLISECONDS)
                    .log { message }
            }
            consoleOutput shouldContainOnlyOnce message
        }

        @Test
        fun `use the latest specified rate`() {
            val invocations = 10
            val intervalMillis = 50L
            val initialRateMillis = 50
            val finalRateMillis = 75

            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .atMostEvery(initialRateMillis, MILLISECONDS)
                        .atMostEvery(finalRateMillis, MILLISECONDS)
                        .log { message }
                    sleep(intervalMillis)
                }
            }

            val expectedTimesLogged = expectedExecutions(invocations, intervalMillis, finalRateMillis)
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe expectedTimesLogged
        }

        @Test
        fun `have no effect on a zero rate`() {
            val invocations = 10
            val timeRateMillis = 0

            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .atMostEvery(timeRateMillis, MILLISECONDS)
                        .log { message }
                }
            }

            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe invocations
        }

        @Test
        fun `throw on negative rate`() {
            for (timeRateMillis in -1 downTo -1000 step 100) {
                assertThrows<IllegalArgumentException> {
                    logger.atInfo()
                        .atMostEvery(timeRateMillis, MILLISECONDS)
                }
            }
        }

        @Test
        fun `log no more often than the rate allows`() {
            val invocations = 10
            val intervalMillis = 120L
            val timeRateMillis = 300

            val timestampedMessage = { millis: Long -> "log message $millis ms." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .atMostEvery(timeRateMillis, MILLISECONDS)
                        .log { timestampedMessage(millis) }
                    sleep(intervalMillis)
                }
            }

            val expectedTimestamps =
                expectedTimestamps(invocations, intervalMillis, timeRateMillis)
            expectedTimestamps.forEach { i ->
                consoleOutput shouldContainOnlyOnce timestampedMessage(i)
            }

            // The logger prints two lines per a message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedTimestamps.size
        }
    }

    @Nested
    internal inner class `, when given multiple rate limiters` {

        @Test
        fun `log with a higher invocation rate`() {
            val invocations = 15
            val intervalMillis = 50L
            val invocationRate = 5

            val timeRateMillis = intervalMillis.toInt()
            val numberedMessage = { i: Int -> "log message #$i." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                var i = 1
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .every(invocationRate)
                        .atMostEvery(timeRateMillis, MILLISECONDS)
                        .log { numberedMessage(i++) }
                    sleep(intervalMillis)
                }
            }

            // Every invocation is guaranteed to pass through the interval rate,
            // so logging should occur every 5 logs due to invocation rate.

            val expectedMessages = (1..invocations step invocationRate).toList()
            expectedMessages.forEach { i ->
                consoleOutput shouldContainOnlyOnce numberedMessage(i)
            }

            // The logger prints two lines per a message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedMessages.size
        }

        @Test
        fun `log with a longer interval rate`() {
            val invocations = 15
            val intervalMillis = 50L
            val timeRateMillis = 200

            val invocationRate = timeRateMillis / intervalMillis.toInt()
            val timestampedMessage = { millis: Long -> "log message $millis ms." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .every(invocationRate)
                        .atMostEvery(timeRateMillis, MILLISECONDS)
                        .log { timestampedMessage(millis) }
                    sleep(intervalMillis)
                }
            }

            println(consoleOutput)

            // Every invocation is guaranteed to pass through the invocation rate,
            // so logging should occur every 200ms due to interval rate.

            val expectedTimestamps =
                expectedTimestamps(invocations, intervalMillis, timeRateMillis)
            expectedTimestamps.forEach { i ->
                consoleOutput shouldContainOnlyOnce timestampedMessage(i)
            }

            // The logger prints two lines per a message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedTimestamps.size
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
 * Calculates how many times a logging statement will be executed when
 * its execution rate is limited by [LoggingApi.every] method.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param rate
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
private fun expectedExecutions(invocations: Int, rate: Int): Int {
    val sureExecutions = invocations / rate
    val hasRemainder = invocations % rate != 0
    return sureExecutions + if (hasRemainder) 1 else 0
}

/**
 * Calculates how many times a logging statement will be executed when
 * its execution rate is limited by [LoggingApi.atMostEvery] method.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param intervalMillis
 *          time interval between each two invocations
 * @param timeRateMillis
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
private fun expectedExecutions(invocations: Int, intervalMillis: Long, timeRateMillis: Int): Int {
    var result = 1
    var lastInvocationMillis = 0L
    var elapsedMillis = 0L

    repeat(invocations - 1) {
        elapsedMillis += intervalMillis
        if (elapsedMillis - lastInvocationMillis >= timeRateMillis) {
            lastInvocationMillis = elapsedMillis
            result++
        }
    }

    return result
}

/**
 * Calculates the expected timestamps at which the logging statement should be
 * executed when [interval][LoggingApi.atMostEvery] rate limit is configured.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param intervalMillis
 *          time interval between each two invocations
 * @param timeRateMillis
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
private fun expectedTimestamps(
    invocations: Int,
    intervalMillis: Long,
    timeRateMillis: Int,
): List<Long> {

    val result = mutableListOf(0L)
    var currentMillis = 0L
    var lastLoggedMillis = 0L

    for (i in 1..invocations) {
        if (currentMillis >= lastLoggedMillis + timeRateMillis) {
            result.add(currentMillis)
            lastLoggedMillis = currentMillis
        }
        currentMillis += intervalMillis

    }

    return result
}
