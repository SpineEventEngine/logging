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
import io.spine.logging.given.InvocationsPerSite
import io.spine.logging.given.Task
import io.spine.logging.given.Task.*
import io.spine.logging.given.domain.AnnotatedClass
import io.spine.logging.given.expectedRuns
import io.spine.logging.given.expectedTimestamps
import io.spine.logging.given.randomLogSite
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
    internal inner class `when given an invocation rate limit,` {

        @Test
        fun `always log on the first invocation`() {
            val rateLimit = 5
            val consoleOutput = tapConsole {
                logger.atInfo()
                    .every(rateLimit)
                    .log { message }
            }
            consoleOutput shouldContainOnlyOnce message
        }

        @Test
        fun `use the latest specified rate`() {
            val invocations = 10
            val firstRateLimit = 3
            val lastRateLimit = 5

            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .every(firstRateLimit)
                        .every(lastRateLimit)
                        .log { message }
                }
            }

            val expectedTimesLogged = expectedRuns(invocations, lastRateLimit)
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe expectedTimesLogged
        }

        @Test
        fun `throw on zero rate`() {
            val rateLimit = 0
            assertThrows<IllegalArgumentException> {
                logger.atInfo()
                    .every(rateLimit)
            }
        }

        @Test
        fun `throw on negative rates`() {
            for (rateLimit in -1 downTo -5) {
                assertThrows<IllegalArgumentException> {
                    logger.atInfo()
                        .every(rateLimit)
                }
            }
        }

        @Test
        fun `log no more often than the rate allows`() {
            val invocations = 28
            val rateLimit = 5

            val numberedMessage = { i: Int -> "log message #$i." }
            val consoleOutput = tapConsole {
                (1..invocations).forEach { i ->
                    logger.atInfo()
                        .every(rateLimit)
                        .log { numberedMessage(i) }
                }
            }

            val expectedInvocations = (1..invocations step rateLimit).toList()
            expectedInvocations.forEach { i ->
                consoleOutput shouldContainOnlyOnce numberedMessage(i)
            }

            // The logger prints two lines per message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedInvocations.size
        }
    }

    @Nested
    internal inner class `when given a time rate limit,` {

        @Test
        fun `always log on the first invocation`() {
            val rateLimitMillis = 100
            val consoleOutput = tapConsole {
                logger.atInfo()
                    .atMostEvery(rateLimitMillis, MILLISECONDS)
                    .log { message }
            }
            consoleOutput shouldContainOnlyOnce message
        }

        @Test
        fun `use the latest specified rate`() {
            val invocations = 10
            val intervalMillis = 50L
            val firstRateLimitMillis = 50
            val lastRateLimitMillis = 100

            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .atMostEvery(firstRateLimitMillis, MILLISECONDS)
                        .atMostEvery(lastRateLimitMillis, MILLISECONDS)
                        .log { message }
                    sleep(intervalMillis)
                }
            }

            val expectedTimesLogged = expectedRuns(invocations, intervalMillis, lastRateLimitMillis)
            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe expectedTimesLogged
        }

        @Test
        fun `have no effect on a zero rate`() {
            val invocations = 10
            val rateLimitMillis = 0

            val consoleOutput = tapConsole {
                repeat(invocations) {
                    logger.atInfo()
                        .atMostEvery(rateLimitMillis, MILLISECONDS)
                        .log { message }
                }
            }

            val timesLogged = consoleOutput.occurrencesOf(message)
            timesLogged shouldBe invocations
        }

        @Test
        fun `throw on negative rate`() {
            for (rateLimitMillis in -1 downTo -1000 step 100) {
                assertThrows<IllegalArgumentException> {
                    logger.atInfo()
                        .atMostEvery(rateLimitMillis, MILLISECONDS)
                }
            }
        }

        @Test
        fun `log no more often than the rate allows`() {
            val invocations = 10
            val intervalMillis = 120L
            val rateLimitMillis = 300

            val timestampedMessage = { millis: Long -> "log message $millis ms." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .atMostEvery(rateLimitMillis, MILLISECONDS)
                        .log { timestampedMessage(millis) }
                    sleep(intervalMillis)
                }
            }

            val expectedTimestamps =
                expectedTimestamps(invocations, intervalMillis, rateLimitMillis)
            expectedTimestamps.forEach { i ->
                consoleOutput shouldContainOnlyOnce timestampedMessage(i)
            }

            // The logger prints two lines per message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedTimestamps.size
        }
    }

    @Nested
    internal inner class `when given multiple rate limiters` {

        @Test
        fun `log with a higher invocation rate`() {
            val invocations = 15
            val intervalMillis = 50L
            val invocationLimit = 5

            val timeLimitMillis = intervalMillis.toInt()
            val numberedMessage = { i: Int -> "log message #$i." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                var i = 1
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .every(invocationLimit)
                        .atMostEvery(timeLimitMillis, MILLISECONDS)
                        .log { numberedMessage(i++) }
                    sleep(intervalMillis)
                }
            }

            // Every invocation is guaranteed to pass through the time rate,
            // so logging should occur every 5 logs due to invocation rate.

            val expectedMessages = (1..invocations step invocationLimit).toList()
            expectedMessages.forEach { i ->
                consoleOutput shouldContainOnlyOnce numberedMessage(i)
            }

            // The logger prints two lines per message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedMessages.size
        }

        @Test
        fun `log with a longer time rate`() {
            val invocations = 15
            val intervalMillis = 50L
            val invocationLimit = 200

            val timeLimitMillis = invocationLimit / intervalMillis.toInt()
            val timestampedMessage = { millis: Long -> "log message $millis ms." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .every(timeLimitMillis)
                        .atMostEvery(invocationLimit, MILLISECONDS)
                        .log { timestampedMessage(millis) }
                    sleep(intervalMillis)
                }
            }

            // Every invocation is guaranteed to pass through the invocation rate,
            // so logging should occur every 200ms due to time rate.

            val expectedTimestamps =
                expectedTimestamps(invocations, intervalMillis, invocationLimit)
            expectedTimestamps.forEach { i ->
                consoleOutput shouldContainOnlyOnce timestampedMessage(i)
            }

            // The logger prints two lines per message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedTimestamps.size
        }

        @Test
        fun `log on intersection of time and invocation rates`() {
            val invocations = 15
            val intervalMillis = 50L
            val timeLimitMillis = 200
            val invocationLimit = 3

            val timestampedMessage = { millis: Long -> "log message $millis ms." }
            val totalDuration = (invocations - 1) * intervalMillis
            val consoleOutput = tapConsole {
                for (millis in 0..totalDuration step intervalMillis) {
                    logger.atInfo()
                        .every(invocationLimit)
                        .atMostEvery(timeLimitMillis, MILLISECONDS)
                        .log { timestampedMessage(millis) }
                    sleep(intervalMillis)
                }
            }

            // Every invocation should firstly pass the time rate, then it
            // starts counting for the invocation rate. And only then emitted.

            val expectedTimestamps =
                expectedTimestamps(invocations, intervalMillis, timeLimitMillis, invocationLimit)
            expectedTimestamps.forEach { i ->
                consoleOutput shouldContainOnlyOnce timestampedMessage(i)
            }

            // The logger prints two lines per message.
            val timesLogged = consoleOutput.lines().size / 2
            timesLogged shouldBe expectedTimestamps.size
        }
    }

    @Nested
    internal inner class `count an invocation rate limit` {

        @Test
        fun `per enum value`() {
            val rateLimit = 3
            val invocationsPerTask = mutableMapOf(
                BUILD to 15,
                DESTROY to 7,
                ARCHIVE to 11,
            )

            val taskedMessage = { task: Task -> "$task log message" }
            val consoleOutput = tapConsole {
                invocationsPerTask.forEach { (task, invocations) ->
                    repeat(invocations) {
                        logger.atInfo()
                            .per(task)
                            .every(rateLimit)
                            .log { taskedMessage(task) }
                    }
                }
            }

            val expectedLogsPerTask = expectedRuns(invocationsPerTask, rateLimit)
            val logsPerTask = invocationsPerTask.mapValues { (task, _) ->
                val wantedMessage = taskedMessage(task)
                consoleOutput.occurrencesOf(wantedMessage)
            }
            logsPerTask shouldBe expectedLogsPerTask
        }

        @Test
        fun `per combination of enum values`() {
            val rateLimit = 3
            val invocationsPerTasks = mutableMapOf(
                setOf(BUILD, DESTROY, REVISE) to 15,
                setOf(BUILD, UPDATE, DESTROY) to 7,
                setOf(ARCHIVE, BUILD, DESTROY) to 11,
            )

            val taskedMessage = { tasks: Set<Task> -> "$tasks log message" }
            val consoleOutput = tapConsole {
                invocationsPerTasks.forEach { (tasks, invocations) ->
                    val tasksList = tasks.toList()
                    repeat(invocations) {
                        logger.atInfo()
                            .per(tasksList[0])
                            .per(tasksList[1])
                            .per(tasksList[2])
                            .every(rateLimit)
                            .log { taskedMessage(tasks) }
                    }
                }
            }

            val expectedLogsPerTasks = expectedRuns(invocationsPerTasks, rateLimit)
            val logsPerTasks = invocationsPerTasks.mapValues { (tasks, _) ->
                val wantedMessage = taskedMessage(tasks)
                consoleOutput.occurrencesOf(wantedMessage)
            }
            logsPerTasks shouldBe expectedLogsPerTasks
        }
    }

    @Nested
    internal inner class `when given a custom log site` {

        @Test
        fun `track metadata per log site`() {
            val invocationsPerSite = InvocationsPerSite()
                .add(randomLogSite(), rate = 5, invocations = 15)
                .add(randomLogSite(), rate = 3, invocations = 7)
                .add(randomLogSite(), rate = 4, invocations = 17)

            val sitedMessage = { site: LogSite -> "$site log message" }
            val consoleOutput = tapConsole {
                invocationsPerSite.forEach { (site, rate, invocations) ->
                    repeat(invocations) {
                        logger.atInfo()
                            .withInjectedLogSite(site)
                            .every(rate)
                            .log { sitedMessage(site) }
                    }
                }
            }

            val expectedLogsPerSite = expectedRuns(invocationsPerSite)
            val logsPerSite = invocationsPerSite.associate { (logSite) ->
                val wantedMessage = sitedMessage(logSite)
                val occurrences = consoleOutput.occurrencesOf(wantedMessage)
                logSite to occurrences
            }

            logsPerSite shouldBe expectedLogsPerSite
        }

        @Test
        fun `use the first specified site`() {
            val firstLogSite = randomLogSite()
            val lastLogSite = randomLogSite()

            val consoleOutput = tapConsole {
                logger.atInfo()
                    .withInjectedLogSite(firstLogSite)
                    .withInjectedLogSite(lastLogSite)
                    .log { message }
            }

            consoleOutput shouldContain firstLogSite.className
            consoleOutput shouldContain firstLogSite.methodName
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
