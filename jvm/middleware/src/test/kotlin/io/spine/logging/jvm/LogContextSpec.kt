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

package io.spine.logging.jvm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.logging.jvm.DurationRateLimiter.newRateLimitPeriod
import io.spine.logging.jvm.LazyArgs.lazy
import io.spine.logging.jvm.LogContext.Key
import io.spine.logging.jvm.LogContext.specializeLogSiteKeyFromMetadata
import io.spine.logging.jvm.backend.given.FakeMetadata
import io.spine.logging.jvm.backend.given.MemoizingLoggerBackend
import io.spine.logging.jvm.backend.given.shouldContain
import io.spine.logging.jvm.backend.given.shouldContainInOrder
import io.spine.logging.jvm.backend.given.shouldHaveSize
import io.spine.logging.jvm.backend.given.shouldNotContain
import io.spine.logging.jvm.backend.given.shouldUniquelyContain
import io.spine.logging.jvm.context.Tags
import io.spine.logging.jvm.given.ConfigurableLogger
import io.spine.logging.jvm.given.FakeLogSite
import io.spine.logging.jvm.given.iterate
import io.spine.logging.jvm.given.shouldHaveArguments
import io.spine.logging.jvm.given.shouldHaveMessage
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.logging.Level.INFO
import java.util.logging.Level.WARNING
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [LogContext].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/LogContextTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`LogContext` should")
internal class LogContextSpec {

    private val backend = MemoizingLoggerBackend()
    private val logger = Middleman(backend)

    companion object {
        // Arbitrary constants of overloaded types for testing argument mappings.
        private const val BYTE_ARG = Byte.MAX_VALUE
        private const val SHORT_ARG = Short.MAX_VALUE
        private const val INT_ARG = Int.MAX_VALUE
        private const val LONG_ARG = Long.MAX_VALUE
        private const val CHAR_ARG = 'X'
        private val OBJECT_ARG = Any()
        private val REPEATED_KEY = repeatedKey<String>("str")
        private val FLAG_KEY = repeatedKey<Boolean>("flag")
        private val ONCE_PER_SECOND = newRateLimitPeriod(1, SECONDS)

        private const val MESSAGE_LITERAL = "Hello World"
        private const val MESSAGE_PATTERN = "Hello %s"
        private const val MESSAGE_ARGUMENT = "World"

        // In normal use, the logger would never need to be passed in,
        // and you'd use `logVarargs()`.
        private fun logHelper(
            logger: Middleman,
            logSite: JvmLogSite,
            n: Int,
            message: String
        ) {
            logger.atInfo()
                .withInjectedLogSite(logSite)
                .every(n)
                .log("%s", message)
        }
    }

    @Test
    fun `return 'true' if logging is enabled at the implied level`() {
        backend.setLevel(INFO)
        logger.atFine().isEnabled().shouldBeFalse()
        logger.atInfo().isEnabled().shouldBeTrue()
        logger.at(WARNING).isEnabled().shouldBeTrue()
    }

    @Test
    fun `log with the given cause`() {
        val cause = Throwable()
        logger.atInfo()
            .withCause(cause)
            .log(MESSAGE_LITERAL)
        backend.lastLogged.metadata shouldHaveSize 1
        backend.lastLogged.metadata.shouldUniquelyContain(Key.LOG_CAUSE, cause)
        backend.lastLogged shouldHaveMessage MESSAGE_LITERAL
    }

    @Test
    fun `lazily evaluate arguments`() {
        logger.atInfo().log(MESSAGE_PATTERN, lazy { MESSAGE_ARGUMENT })
        logger.atFine().log(
            MESSAGE_PATTERN,
            lazy { error("Lazy arguments should not be evaluated in a disabled log statement") }
        )
        backend.lastLogged shouldHaveMessage MESSAGE_PATTERN
        backend.lastLogged.shouldHaveArguments(MESSAGE_ARGUMENT)
    }

    @Test
    fun `accept a formatted message`() {
        logger.at(INFO).log(MESSAGE_PATTERN, MESSAGE_ARGUMENT)
        backend.loggedCount shouldBe 1
        backend.lastLogged shouldHaveMessage MESSAGE_PATTERN
        backend.lastLogged.shouldHaveArguments(MESSAGE_ARGUMENT)

        // Should NOT ask for literal argument as none exists.
        shouldThrow<IllegalStateException> {
            backend.lastLogged.literalArgument
        }
    }

    @Test
    fun `accept a literal message`() {
        logger.at(INFO).log(MESSAGE_LITERAL)
        backend.loggedCount shouldBe 1
        backend.lastLogged shouldHaveMessage MESSAGE_LITERAL

        // Cannot ask for format arguments as none exist.
        backend.lastLogged.templateContext.shouldBeNull()
        shouldThrow<IllegalStateException> {
            backend.lastLogged.arguments
        }
    }

    @Test
    fun `accept multiple metadata entries`() {
        val cause = RuntimeException()
        val invocations = 42
        logger.atInfo()
            .withCause(cause)
            .every(invocations)
            .log(MESSAGE_LITERAL)
        backend.loggedCount shouldBe 1
        backend.lastLogged.metadata shouldHaveSize 2 // Cause and rate.
        backend.lastLogged.metadata.shouldUniquelyContain(Key.LOG_EVERY_N, invocations)
        backend.lastLogged.metadata.shouldUniquelyContain(Key.LOG_CAUSE, cause)
    }

    @Test
    fun `handle multiple metadata keys`() {
        val values = arrayOf("foo", "bar")
        logger.atInfo()
            .with(REPEATED_KEY, values[0])
            .with(REPEATED_KEY, values[1])
            .log()
        backend.lastLogged.metadata.shouldContainInOrder(REPEATED_KEY, *values)
    }

    @Test
    fun `handle tag keys`() {
        logger.atInfo()
            .with(FLAG_KEY)
            .log()
        backend.lastLogged.metadata.shouldUniquelyContain(FLAG_KEY, true)
        logger.atInfo()
            .with(FLAG_KEY, false)
            .log()
        backend.lastLogged.metadata.shouldUniquelyContain(FLAG_KEY, false)
    }

    @Test
    fun `handle interleaved metadata keys`() {
        val values = arrayOf("foo", "bar")
        logger.atInfo()
            .with(REPEATED_KEY, values[0])
            .with(FLAG_KEY)
            .with(REPEATED_KEY, values[1])
            .log()
        backend.lastLogged.metadata.shouldContainInOrder(REPEATED_KEY, *values)
        backend.lastLogged.metadata.shouldUniquelyContain(FLAG_KEY, true)
    }

    /**
     * For testing log-site tags are correctly merged with metadata,
     * see [AbstractContextDataProviderSpec][io.spine.logging.jvm.context.AbstractContextDataProviderSpec].
     */
    @Test
    fun `accept tags`() {
        val tags = Tags.of("foo", "bar")
        logger.atInfo()
            .with(Key.TAGS, tags)
            .log("With tags")
        backend.loggedCount shouldBe 1
        backend.lastLogged.metadata.shouldUniquelyContain(Key.TAGS, tags)
    }

    @Test
    fun `log once per the given number of invocations`() {
        val backend = MemoizingLoggerBackend()
        val logger = ConfigurableLogger(backend)
        val startNanos = currentTimeNanos()

        // Logging occurs for counts: 0, 5, 10 (timestamp is not important).
        for ((counter, millis) in (0..1000L step 100).withIndex()) {
            val timestampNanos = startNanos + MILLISECONDS.toNanos(millis)
            logger.at(INFO, timestampNanos)
                .every(5)
                .log("Count=%d", counter)
        }

        backend.loggedCount shouldBe 3

        // The first log we captured should be the first one emitted.
        backend.firstLogged.timestampNanos shouldBe startNanos
        backend.firstLogged.metadata.shouldUniquelyContain(Key.LOG_EVERY_N, 5)

        // Check the expected count and skipped-count for each log.
        backend.logged[0].shouldHaveArguments(0)
        backend.logged[0].metadata.shouldNotContain(Key.SKIPPED_LOG_COUNT)
        backend.logged[1].shouldHaveArguments(5)
        backend.logged[1].metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, 4)
        backend.logged[2].shouldHaveArguments(10)
        backend.logged[2].metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, 4)
    }

    @Test
    fun `log with likelihood 1 in 'n'`() {
        val backend = MemoizingLoggerBackend()
        val logger = ConfigurableLogger(backend)
        val startNanos = currentTimeNanos()

        // Logging occurs randomly 1-in-5 times over 1000 log statements.
        for ((counter, millis) in (0..1000L).withIndex()) {
            val timestampNanos = startNanos + MILLISECONDS.toNanos(millis)
            logger.at(INFO, timestampNanos)
                .onAverageEvery(5)
                .log("Count=%d", counter)
        }

        // Statistically impossible that we randomly get +/- 100 over 1000 logs.
        backend.loggedCount shouldBeInRange 100..300
        backend.firstLogged.metadata.shouldUniquelyContain(Key.LOG_SAMPLE_EVERY_N, 5)

        // Check the expected count and skipped-count for each log based on the timestamp.
        var lastLogIndex = -1
        for (n in 0..<backend.loggedCount) {
            // The timestamp increases by 1 millisecond each time,
            // so we can get the log index from it.
            val deltaNanos = backend.logged[n].timestampNanos - startNanos
            val logIndex = (deltaNanos / MILLISECONDS.toNanos(1)).toInt()
            backend.logged[n].shouldHaveArguments(logIndex)

            // This works even if `lastLogIndex` == -1.
            val skipped = logIndex - lastLogIndex - 1
            val metadata = backend.logged[n].metadata
            if (skipped == 0) {
                metadata.shouldNotContain(Key.SKIPPED_LOG_COUNT)
            } else {
                metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, skipped)
            }

            lastLogIndex = logIndex
        }
    }

    @Test
    fun `log at most once per the specified time period`() {
        val backend = MemoizingLoggerBackend()
        val logger = ConfigurableLogger(backend)
        val startNanos = currentTimeNanos()

        // Logging occurs at: +0ms, +2400ms, +4800ms.
        // Note it will not occur at 4200ms, which is the first logging attempt after the
        // 2nd multiple of 2 seconds because the timestamp is reset to be (start + 2400ms)
        // and not (start + 2000ms). `atMostEvery()` does not rate limit over multiple samples.
        for ((counter, millis) in (0..5000L step 600).withIndex()) {
            val timestampNanos = startNanos + MILLISECONDS.toNanos(millis)
            logger.at(INFO, timestampNanos)
                .atMostEvery(2, SECONDS)
                .log("Count=%d", counter)
        }

        backend.loggedCount shouldBe 3

        // Check the first log we captured was the first one emitted.
        backend.firstLogged.timestampNanos shouldBe startNanos
        backend.firstLogged.metadata.shouldUniquelyContain(
            Key.LOG_AT_MOST_EVERY,
            newRateLimitPeriod(2, SECONDS)
        )

        // Check the expected count and skipped-count for each log.
        backend.logged[0].shouldHaveArguments(0)
        backend.logged[0].metadata.shouldNotContain(Key.SKIPPED_LOG_COUNT)
        backend.logged[1].shouldHaveArguments(4)
        backend.logged[1].metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, 3)
        backend.logged[2].shouldHaveArguments(8)
        backend.logged[2].metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, 3)
    }

    @Nested
    inner class
    `when given multiple rate limiters` {

        @Test
        fun `log with a higher invocation rate`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)
            val startNanos = currentTimeNanos()

            // 10 logs per second over 6 seconds.
            for ((counter, millis) in (0..6000L step 100).withIndex()) {
                val timestampNanos = startNanos + MILLISECONDS.toNanos(millis)
                // More than N logs occur per rate limit period,
                // so logging should occur every 2 seconds.
                logger.at(INFO, timestampNanos)
                    .every(15)
                    .atMostEvery(2, SECONDS)
                    .log("Count=%d", counter)
            }

            backend.loggedCount shouldBe 4
            backend.logged[0].shouldHaveArguments(0)
            backend.logged[1].shouldHaveArguments(20)
            backend.logged[2].shouldHaveArguments(40)
            backend.logged[3].shouldHaveArguments(60)
            backend.logged[3].metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, 19)
        }

        @Test
        fun `log with a lower invocation rate`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)
            val startNanos = currentTimeNanos()

            // 10 logs per second over 6 seconds.
            for ((counter, millis) in (0..6000L step 100).withIndex()) {
                val timestampNanos = startNanos + MILLISECONDS.toNanos(millis)
                // Fever than N logs occur in the rate limit period,
                // so logging should occur every 15 logs.
                logger.at(INFO, timestampNanos)
                    .every(15)
                    .atMostEvery(1, SECONDS)
                    .log("Count=%d", counter)
            }

            backend.loggedCount shouldBe 5
            backend.logged[0].shouldHaveArguments(0)
            backend.logged[1].shouldHaveArguments(15)
            backend.logged[2].shouldHaveArguments(30)
            backend.logged[3].shouldHaveArguments(45)
            backend.logged[4].shouldHaveArguments(60)
            backend.logged[4].metadata.shouldUniquelyContain(Key.SKIPPED_LOG_COUNT, 14)
        }
    }

    @Nested
    inner class
    `aggregate stateful logging with respect to` {

        @Test
        fun `bucketing strategy`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)

            // Logs for both types should appear.
            // Even though the 2nd log is within the rate limit period.
            // NOTE: It is important this is tested on a single log statement.
            var nowNanos = currentTimeNanos()
            listOf(
                IllegalArgumentException(),
                NullPointerException(),
                NullPointerException(),
                IllegalArgumentException(),
            ).forEach { exception ->
                logger.at(INFO, nowNanos)
                    .atMostEvery(1, SECONDS)
                    .per(exception, LogPerBucketingStrategy.byClass())
                    .log("Err: %s", exception.message)
                nowNanos += MILLISECONDS.toNanos(100)
            }

            backend.loggedCount shouldBe 2

            backend.logged[0].metadata.shouldHaveSize(2)
            backend.logged[0].metadata.shouldUniquelyContain(
                Key.LOG_SITE_GROUPING_KEY,
                IllegalArgumentException::class.java
            )
            backend.logged[0].metadata.shouldUniquelyContain(
                Key.LOG_AT_MOST_EVERY,
                ONCE_PER_SECOND
            )

            backend.logged[1].metadata.shouldHaveSize(2)
            backend.logged[1].metadata.shouldUniquelyContain(
                Key.LOG_SITE_GROUPING_KEY,
                NullPointerException::class.java
            )
            backend.logged[1].metadata.shouldUniquelyContain(
                Key.LOG_AT_MOST_EVERY,
                ONCE_PER_SECOND
            )
        }

        @Test
        fun `enum constant`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)

            // Logs for both types should appear.
            // Even though the 2nd log is within the rate limit period.
            // NOTE: It is important this is tested on a single log statement.
            var nowNanos = currentTimeNanos()
            listOf(
                LogType.FOO,
                LogType.FOO,
                LogType.FOO,
                LogType.BAR,
                LogType.FOO,
                LogType.BAR,
                LogType.FOO
            ).forEach { type ->
                logger.at(INFO, nowNanos)
                    .atMostEvery(1, SECONDS)
                    .per(type)
                    .log("Type: %s", type)
                nowNanos += MILLISECONDS.toNanos(100)
            }

            backend.loggedCount shouldBe 2

            backend.logged[0].shouldHaveArguments(LogType.FOO)
            backend.logged[0].metadata.shouldHaveSize(2)
            backend.logged[0].metadata.shouldUniquelyContain(Key.LOG_SITE_GROUPING_KEY, LogType.FOO)
            backend.logged[0].metadata.shouldUniquelyContain(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)

            backend.logged[1].shouldHaveArguments(LogType.BAR)
            backend.logged[1].metadata.shouldHaveSize(2)
            backend.logged[1].metadata.shouldUniquelyContain(Key.LOG_SITE_GROUPING_KEY, LogType.BAR)
            backend.logged[1].metadata.shouldUniquelyContain(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
        }

        @Test
        fun `scope provider`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)

            // We can't test a specific implementation of `ScopedLoggingContext` here,
            // so we fake it. The `ScopedLoggingContext` behavior is well tested elsewhere.
            // Only tests should ever create “immediate providers” like this
            // as it doesn't make sense otherwise.
            var nowNanos = currentTimeNanos()
            val fooScope = LoggingScope.create("foo")
            val barScope = LoggingScope.create("bar")
            val foo = LoggingScopeProvider { fooScope }
            val bar = LoggingScopeProvider { barScope }

            // Logs for both scopes should appear.
            // Even though the 2nd log is within the rate limit period.
            // NOTE: It is important this is tested on a single log statement.
            listOf(foo, foo, foo, bar, foo, bar, foo).forEach { provider ->
                logger.at(INFO, nowNanos)
                    .atMostEvery(1, SECONDS)
                    .per(provider)
                    .log("message")
                nowNanos += MILLISECONDS.toNanos(100)
            }

            backend.loggedCount shouldBe 2

            backend.logged[0].metadata.shouldHaveSize(2)
            backend.logged[0].metadata.shouldUniquelyContain(Key.LOG_SITE_GROUPING_KEY, fooScope)
            backend.logged[0].metadata.shouldUniquelyContain(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)

            backend.logged[1].metadata.shouldHaveSize(2)
            backend.logged[1].metadata.shouldUniquelyContain(Key.LOG_SITE_GROUPING_KEY, barScope)
            backend.logged[1].metadata.shouldUniquelyContain(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
        }
    }

    @Nested
    inner class
    `force logging at the given level` {

        @Test
        fun `without any rate limiting`() {
            val backend = MemoizingLoggerBackend()
            backend.setLevel(WARNING)
            val logger = ConfigurableLogger(backend)
            logger.forceAt(INFO).log("LOGGED")
            backend.loggedCount shouldBe 1
            backend.logged[0].shouldHaveMessage("LOGGED")
            backend.logged[0].metadata.shouldHaveSize(1)
            backend.logged[0].metadata.shouldUniquelyContain(Key.WAS_FORCED, true)
            backend.logged[0].wasForced().shouldBeTrue()
        }

        @Test
        fun `with 'every(n)' limiter`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)
            val logSite = FakeLogSite("com.example.MyClass", "everyN", 123, null)

            logger.atInfo()
                .every(3)
                .withInjectedLogSite(logSite) // Note that the log site is passed explicitly.
                .log("LOGGED 1") // Log statements always get logged the first time.

            // Not logged due to rate limiting.
            logger.atInfo()
                .every(3)
                .withInjectedLogSite(logSite)
                .log("NOT LOGGED")

            // Manually create the forced context (there is no “normal” API for this).
            logger.forceAt(INFO)
                .every(3)
                .withInjectedLogSite(logSite)
                .log("LOGGED 2")

            // This shows that the “forced” context does not count towards the rate limit count.
            // Otherwise, this log statement would have been logged.
            logger.atInfo()
                .every(3)
                .withInjectedLogSite(logSite)
                .log("NOT LOGGED")

            backend.loggedCount shouldBe 2
            backend.logged[0].shouldHaveMessage("LOGGED 1")
            backend.logged[1].shouldHaveMessage("LOGGED 2")
            backend.logged[1].metadata.shouldHaveSize(1)
            backend.logged[1].metadata.shouldUniquelyContain(Key.WAS_FORCED, true)
        }

        @Test
        fun `with 'atMostEvery(n)' limiter`() {
            val backend = MemoizingLoggerBackend()
            val logger = ConfigurableLogger(backend)
            val logSite = FakeLogSite("com.example.MyClass", "atMostEvery", 123, null)

            var nowNanos = currentTimeNanos()
            logger.at(INFO, nowNanos)
                .atMostEvery(1, SECONDS)
                .withInjectedLogSite(logSite) // Note that the log site is passed explicitly.
                .log("LOGGED 1") // Log statements always get logged the first time.

            // Not logged due to rate limiting.
            nowNanos += MILLISECONDS.toNanos(100)
            logger.at(INFO, nowNanos)
                .atMostEvery(1, SECONDS)
                .withInjectedLogSite(logSite)
                .log("NOT LOGGED")

            // Manually create the forced context (there is no “normal” API for this).
            nowNanos += MILLISECONDS.toNanos(100)
            logger.forceAt(INFO, nowNanos)
                .atMostEvery(1, SECONDS)
                .withInjectedLogSite(logSite)
                .log("LOGGED 2")

            // Not logged due to rate limiting.
            nowNanos += MILLISECONDS.toNanos(100)
            logger.at(INFO, nowNanos)
                .atMostEvery(1, SECONDS)
                .withInjectedLogSite(logSite)
                .log("NOT LOGGED")

            backend.loggedCount shouldBe 2
            backend.logged[0].shouldHaveMessage("LOGGED 1")
            backend.logged[0].metadata.shouldHaveSize(1)
            backend.logged[0].metadata.shouldUniquelyContain(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
            backend.logged[1].shouldHaveMessage("LOGGED 2")
            backend.logged[1].metadata.shouldHaveSize(1)
            backend.logged[1].metadata.shouldUniquelyContain(Key.WAS_FORCED, true)
        }
    }

    // These tests verify that the mapping between the logging context,
    // and the backend preserves arguments as expected.

    @Test
    fun `accept formatting arguments as array`() {
        val args = arrayOf<Any?>("foo", null, "baz")
        logger.atInfo().logVarargs("Any message ...", args)
        backend.lastLogged.shouldHaveArguments(*args)

        // Make sure we took a copy of the arguments rather than risk re-using them.
        backend.loggedCount shouldBe 1
        backend.lastLogged.arguments shouldNotBeSameInstanceAs args
    }

    @Test
    fun `log an empty message without arguments`() {
        logger.atInfo().log()
        backend.lastLogged.shouldHaveMessage("")
        backend.lastLogged.shouldHaveArguments()
    }

    @Test
    fun `not escape percent char when given no arguments`() {
        logger.atInfo().log(MESSAGE_PATTERN)
        backend.lastLogged.shouldHaveMessage(MESSAGE_PATTERN)
        backend.lastLogged.shouldHaveArguments()
    }

    /**
     * Tests that a `null` literal is passed unmodified to the backend
     * without throwing an exception.
     */
    @Test
    fun `accept a nullable literal`() {
        // We want to call `log(String)`, not `log(Object)` with a null value.
        logger.atInfo().log(null as String?)
        backend.lastLogged.shouldHaveMessage(null)
    }

    /**
     * Tests that `null` arguments are passed unmodified to the backend
     * without throwing an exception.
     */
    @Test
    fun `accept a nullable argument`() {
        logger.atInfo().log(MESSAGE_PATTERN, null)
        backend.lastLogged.let {
            it shouldHaveMessage (MESSAGE_PATTERN)
            it.shouldHaveArguments(null)
        }
    }

    @Test
    fun `log 'null' if given message and argument are 'null' simultaneously`() {
        logger.atInfo().log(null, null)
        backend.lastLogged.let {
            it shouldHaveMessage ("<null>")
            it.shouldHaveArguments(null)
        }
    }

    @Test
    fun `provide shortcuts for passing up to 12 arguments`() {
        val msg = "Any message will do..."

        // Verify that the arguments passed in to the object-based methods
        // are mapped correctly.
        logger.atInfo().log(msg, "1")
        backend.lastLogged.shouldHaveArguments("1")
        logger.atInfo().log(msg, "1", "2")
        backend.lastLogged.shouldHaveArguments("1", "2")
        logger.atInfo().log(msg, "1", "2", "3")
        backend.lastLogged.shouldHaveArguments("1", "2", "3")
        logger.atInfo().log(msg, "1", "2", "3", "4")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4")
        logger.atInfo().log(msg, "1", "2", "3", "4", "5")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4", "5")
        logger.atInfo().log(msg, "1", "2", "3", "4", "5", "6")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4", "5", "6")
        logger.atInfo().log(msg, "1", "2", "3", "4", "5", "6", "7")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4", "5", "6", "7")
        logger.atInfo().log(msg, "1", "2", "3", "4", "5", "6", "7", "8")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4", "5", "6", "7", "8")
        logger.atInfo().log(msg, "1", "2", "3", "4", "5", "6", "7", "8", "9")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4", "5", "6", "7", "8", "9")
        logger.atInfo().log(msg, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        backend.lastLogged.shouldHaveArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        logger.atInfo()
            .log(msg, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
        backend.lastLogged
            .shouldHaveArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
        logger.atInfo()
            .log(msg, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
        backend.lastLogged
            .shouldHaveArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
    }

    @Test
    fun `provide shortcuts for passing a non-boxed argument`() {
        val msg = "Any message will do..."

        // Verify arguments passed in to the non-boxed fundamental type methods
        // are mapped correctly.
        logger.atInfo().log(msg, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG)
        logger.atInfo().log(msg, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG)
        logger.atInfo().log(msg, INT_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG)
        logger.atInfo().log(msg, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG)
        logger.atInfo().log(msg, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG)
    }

    @Test
    fun `provide shortcuts for passing two non-boxed arguments`() {
        val msg = "Any message will do..."

        // Verify arguments passed in to the non-boxed fundamental type methods
        // are mapped correctly.
        logger.atInfo().log(msg, BYTE_ARG, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG, BYTE_ARG)
        logger.atInfo().log(msg, BYTE_ARG, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG, SHORT_ARG)
        logger.atInfo().log(msg, BYTE_ARG, INT_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG, INT_ARG)
        logger.atInfo().log(msg, BYTE_ARG, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG, LONG_ARG)
        logger.atInfo().log(msg, BYTE_ARG, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG, CHAR_ARG)
        logger.atInfo().log(msg, SHORT_ARG, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG, BYTE_ARG)
        logger.atInfo().log(msg, SHORT_ARG, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG, SHORT_ARG)
        logger.atInfo().log(msg, SHORT_ARG, INT_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG, INT_ARG)
        logger.atInfo().log(msg, SHORT_ARG, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG, LONG_ARG)
        logger.atInfo().log(msg, SHORT_ARG, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG, CHAR_ARG)
        logger.atInfo().log(msg, INT_ARG, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG, BYTE_ARG)
        logger.atInfo().log(msg, INT_ARG, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG, SHORT_ARG)
        logger.atInfo().log(msg, INT_ARG, INT_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG, INT_ARG)
        logger.atInfo().log(msg, INT_ARG, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG, LONG_ARG)
        logger.atInfo().log(msg, INT_ARG, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG, CHAR_ARG)
        logger.atInfo().log(msg, LONG_ARG, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG, BYTE_ARG)
        logger.atInfo().log(msg, LONG_ARG, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG, SHORT_ARG)
        logger.atInfo().log(msg, LONG_ARG, INT_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG, INT_ARG)
        logger.atInfo().log(msg, LONG_ARG, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG, LONG_ARG)
        logger.atInfo().log(msg, LONG_ARG, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG, CHAR_ARG)
        logger.atInfo().log(msg, CHAR_ARG, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG, BYTE_ARG)
        logger.atInfo().log(msg, CHAR_ARG, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG, SHORT_ARG)
        logger.atInfo().log(msg, CHAR_ARG, INT_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG, INT_ARG)
        logger.atInfo().log(msg, CHAR_ARG, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG, LONG_ARG)
        logger.atInfo().log(msg, CHAR_ARG, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG, CHAR_ARG)
    }

    @Test
    fun `provide shortcuts for passing two mixed, non-boxed arguments`() {
        val ms = "Any message will do..."

        // Verify arguments passed in to the non-boxed fundamental type methods
        // are mapped correctly.
        logger.atInfo().log(ms, OBJECT_ARG, BYTE_ARG)
        backend.lastLogged.shouldHaveArguments(OBJECT_ARG, BYTE_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, SHORT_ARG)
        backend.lastLogged.shouldHaveArguments(OBJECT_ARG, SHORT_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, INT_ARG)
        backend.lastLogged.shouldHaveArguments(OBJECT_ARG, INT_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, LONG_ARG)
        backend.lastLogged.shouldHaveArguments(OBJECT_ARG, LONG_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, CHAR_ARG)
        backend.lastLogged.shouldHaveArguments(OBJECT_ARG, CHAR_ARG)
        logger.atInfo().log(ms, BYTE_ARG, OBJECT_ARG)
        backend.lastLogged.shouldHaveArguments(BYTE_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, SHORT_ARG, OBJECT_ARG)
        backend.lastLogged.shouldHaveArguments(SHORT_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, INT_ARG, OBJECT_ARG)
        backend.lastLogged.shouldHaveArguments(INT_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, LONG_ARG, OBJECT_ARG)
        backend.lastLogged.shouldHaveArguments(LONG_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, CHAR_ARG, OBJECT_ARG)
        backend.lastLogged.shouldHaveArguments(CHAR_ARG, OBJECT_ARG)
    }

    @Test
    fun `log with a stack trace`() {

        // Keep these two lines immediately adjacent to each other.
        val expectedCaller = callerInfoFollowingLine()
        logger.atSevere().withStackTrace(StackSize.FULL).log(MESSAGE_PATTERN, MESSAGE_ARGUMENT)

        backend.loggedCount shouldBe 1
        backend.firstLogged.shouldHaveMessage(MESSAGE_PATTERN)
        backend.firstLogged.shouldHaveArguments(MESSAGE_ARGUMENT)
        backend.firstLogged.metadata.shouldHaveSize(1)
        backend.firstLogged.metadata.shouldContain(Key.LOG_CAUSE)

        val cause = backend.firstLogged.metadata.findValue(Key.LOG_CAUSE)!!
        cause.shouldHaveMessage("FULL")
        cause.cause.shouldBeNull() // It is a synthetic exception.

        val actualStack = listOf(*cause.stackTrace)
        val expectedStack = mutableListOf(*Throwable().stackTrace).also {
            it[0] = expectedCaller // Overwrite the first element to the expected value.
        }

        // Use string representation for comparison since synthetic stack elements
        // are not “equal” to equivalent system stack elements.
        "$actualStack" shouldBe "$expectedStack"
    }

    @Test
    fun `log with a stack trace and a cause`() {
        val badness = RuntimeException("badness")

        // Use “SMALL” size here because we rely on the total stack depth
        // in this test being bigger than that. Using “MEDIUM” or “LARGE” might
        // cause the test to fail when verifying the truncated stack size.
        logger.atInfo()
            .withStackTrace(StackSize.SMALL)
            .withCause(badness)
            .log(MESSAGE_PATTERN, MESSAGE_ARGUMENT)

        backend.loggedCount shouldBe 1
        backend.firstLogged.shouldHaveMessage(MESSAGE_PATTERN)
        backend.firstLogged.shouldHaveArguments(MESSAGE_ARGUMENT)
        backend.firstLogged.metadata.shouldHaveSize(1)
        backend.firstLogged.metadata.shouldContain(Key.LOG_CAUSE)

        val cause = backend.firstLogged.metadata.findValue(Key.LOG_CAUSE)!!
        cause shouldHaveMessage "SMALL"
        cause.stackTrace.size shouldBe StackSize.SMALL.maxDepth
        cause.cause shouldBe badness
    }

    @Test
    fun `explicitly inject the log site`() {
        // Tests if it is the log site instance that controls rate limiting,
        // even over different calls.
        // We don't expect this to ever happen in real code though.
        for (i in 0..6) {
            // Log every 2nd (0, 2, 4, 6)
            logHelper(logger, JvmLogSites.logSite(), 2, "Foo: $i")
            // Log every 3rd (0, 3, 6)
            logHelper(logger, JvmLogSites.logSite(), 3, "Bar: $i")
        }
        backend.loggedCount shouldBe 7
        backend.firstLogged.shouldHaveArguments("Foo: 0")
        backend.logged[1].shouldHaveArguments("Bar: 0")
        backend.logged[2].shouldHaveArguments("Foo: 2")
        backend.logged[3].shouldHaveArguments("Bar: 3")
        backend.logged[4].shouldHaveArguments("Foo: 4")
        backend.logged[5].shouldHaveArguments("Foo: 6")
        backend.logged[6].shouldHaveArguments("Bar: 6")
    }

    /**
     * It is important that injecting an INVALID log site acts as an override
     * to suppress log site analysis (i.e., rate limiting) rather than being a no-op.
     */
    @Test
    fun `suppress an invalid log site analysis`() {
        logger.atInfo()
            .withInjectedLogSite(JvmLogSite.INVALID)
            .log("No log site here")
        logger.atInfo()
            .withInjectedLogSite(null)
            .log("No-op injection")

        backend.loggedCount shouldBe 2
        backend.firstLogged.logSite shouldBe JvmLogSite.INVALID

        backend.logged[1].logSite.shouldNotBeNull()
        backend.logged[1].logSite shouldNotBe JvmLogSite.INVALID
    }

    @Nested inner class
    Specialize {

        @Test
        fun `log site key from a singleton key`() {
            val fooMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo")
            val logSite = FakeLogSite("com.google.foo.Foo", "doFoo", 42, "<unused>")
            val fooKey = specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
            val singletonKey = specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
            fooKey shouldBe singletonKey
        }

        @Test
        fun `log site key from a repeated key`() {
            val fooMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo")
            val repeatedMetadata = FakeMetadata()
                .add(Key.LOG_SITE_GROUPING_KEY, "foo")
                .add(Key.LOG_SITE_GROUPING_KEY, "foo")
            val logSite = FakeLogSite("com.google.foo.Foo", "doFoo", 42, "<unused>")
            val fooKey = specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
            val repeatedKey = specializeLogSiteKeyFromMetadata(logSite, repeatedMetadata)
            fooKey shouldNotBe repeatedKey
        }

        @Test
        fun `distinct log site keys from distinct metadata instances`() {
            val fooMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo")
            val barMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "bar")
            val logSite = FakeLogSite("com.google.foo.Foo", "doFoo", 42, "<unused>")
            val fooKey = specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
            val barKey = specializeLogSiteKeyFromMetadata(logSite, barMetadata)
            fooKey shouldNotBe barKey
        }

        /**
         * This is unfortunate but hard to work around unless [SpecializedLogSiteKey]
         * can be made invariant to the order of specialization (but this class must be
         * very efficient, so that would be hard).
         *
         * This should not be an issue in expected use, since specialization keys should
         * always be applied in the same order at any given log statement.
         */
        @Test
        fun `distinct log site keys from differently ordered metadata instances`() {
            val fooBarMetadata = FakeMetadata()
                .add(Key.LOG_SITE_GROUPING_KEY, "foo")
                .add(Key.LOG_SITE_GROUPING_KEY, "bar")
            val barFooMetadata = FakeMetadata()
                .add(Key.LOG_SITE_GROUPING_KEY, "bar")
                .add(Key.LOG_SITE_GROUPING_KEY, "foo")
            val logSite = FakeLogSite("com.google.foo.Foo", "doFoo", 42, "<unused>")
            val fooBarKey = specializeLogSiteKeyFromMetadata(logSite, fooBarMetadata)
            val barFooKey = specializeLogSiteKeyFromMetadata(logSite, barFooMetadata)
            fooBarKey shouldNotBe barFooKey
        }
    }

    @Test
    fun `provide a grouping key for specialization`() {
        val singletonKey = iterate("foo")
        Key.LOG_SITE_GROUPING_KEY.emitRepeatedForTests(singletonKey) { key: String, value: Any ->
            key shouldBe "group_by"
            value shouldBe "foo"
        }

        // We don't care too much about the case with multiple keys
        // because it is so rare, but it should be vaguely sensible.
        val multipleKeys = iterate("foo", "bar")
        Key.LOG_SITE_GROUPING_KEY.emitRepeatedForTests(multipleKeys) { k: String, v: Any ->
            k shouldBe "group_by"
            v shouldBe "[foo,bar]"
        }
    }
}

/**
 * Returns a [StackTraceElement] that points to the line, following right after
 * the call to this method.
 */
private fun callerInfoFollowingLine(): StackTraceElement {
    // We reference the third element due to intermediate,
    // Kotlin-generated Java classes.
    val caller = Exception().stackTrace[2]
    return StackTraceElement(
        caller.className,
        caller.methodName,
        caller.fileName,
        caller.lineNumber + 1
    )
}

private fun currentTimeNanos(): Long = MILLISECONDS.toNanos(currentTimeMillis())

private enum class LogType {
    FOO,
    BAR
}
