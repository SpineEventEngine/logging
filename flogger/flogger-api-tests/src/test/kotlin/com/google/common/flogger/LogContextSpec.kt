/*
 * Copyright (C) 2016 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.flogger

import com.google.common.collect.Iterators
import com.google.common.flogger.DurationRateLimiter.newRateLimitPeriod
import com.google.common.flogger.LazyArgs.lazy
import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.context.Tags
import com.google.common.flogger.given.shouldContainInOrder
import com.google.common.flogger.given.shouldContain
import com.google.common.flogger.given.shouldHaveArguments
import com.google.common.flogger.given.shouldHaveMessage
import com.google.common.flogger.given.shouldHaveSize
import com.google.common.flogger.given.shouldNotContain
import com.google.common.flogger.given.shouldUniquelyContain
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeLoggerBackend
import com.google.common.flogger.testing.FakeMetadata
import com.google.common.flogger.testing.TestLogger
import com.google.common.truth.Truth.assertThat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.logging.Level.INFO
import java.util.logging.Level.WARNING
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`LogContext` should")
internal class LogContextSpec {

    private val backend = FakeLoggerBackend()
    private val logger = FluentLogger2(backend)

    companion object {
        // Arbitrary constants of overloaded types for testing argument mappings.
        private const val BYTE_ARG = Byte.MAX_VALUE
        private const val SHORT_ARG = Short.MAX_VALUE
        private const val INT_ARG = Int.MAX_VALUE
        private const val LONG_ARG = Long.MAX_VALUE
        private const val CHAR_ARG = 'X'
        private val OBJECT_ARG = Any()
        private val REPEATED_KEY = repeated("str", String::class.java)
        private val FLAG_KEY = repeated("flag", Boolean::class.javaObjectType)
        private val ONCE_PER_SECOND = newRateLimitPeriod(1, SECONDS)

        // In normal use, the logger would never need to be passed in,
        // and you'd use `logVarargs()`.
        private fun logHelper(logger: FluentLogger2, logSite: LogSite, n: Int, message: String) {
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
            .log("Hello World")

        backend.lastLogged.metadata shouldHaveSize 1
        backend.lastLogged.metadata.shouldUniquelyContain(Key.LOG_CAUSE, cause)
        backend.lastLogged shouldHaveMessage "Hello World"
    }

    @Test
    fun `lazily evaluate arguments`() {
        logger.atInfo().log("Hello %s", lazy { "World" })
        logger.atFine().log(
            "Hello %s",
            lazy { error("Lazy arguments should not be evaluated in a disabled log statement") }
        )

        backend.lastLogged shouldHaveMessage "Hello %s"
        backend.lastLogged.shouldHaveArguments("World")
    }

    @Test
    fun `accept a formatted message`() {
        logger.at(INFO).log("Formatted %s", "Message")
        backend.loggedCount shouldBe 1
        backend.lastLogged shouldHaveMessage "Formatted %s"
        backend.lastLogged.shouldHaveArguments("Message")

        // Should NOT ask for literal argument as none exists.
        shouldThrow<IllegalStateException> {
            backend.lastLogged.literalArgument
        }
    }

    @Test
    fun `accept a literal message`() {
        logger.at(INFO).log("Literal Message")
        backend.loggedCount shouldBe 1
        backend.lastLogged shouldHaveMessage "Literal Message"

        // Cannot ask for format arguments as none exist.
        backend.lastLogged.templateContext.shouldBeNull()
        shouldThrow<IllegalStateException> {
            backend.lastLogged.getArguments()
        }
    }

    @Test
    fun `accept multiple metadata entries`() {
        val cause = RuntimeException()
        logger.atInfo()
            .withCause(cause)
            .every(42)
            .log("Hello World")
        backend.loggedCount shouldBe 1
        backend.lastLogged.metadata shouldHaveSize 2
        backend.lastLogged.metadata.shouldUniquelyContain(Key.LOG_EVERY_N, 42)
        backend.lastLogged.metadata.shouldUniquelyContain(Key.LOG_CAUSE, cause)
    }

    @Test
    fun `handle multiple metadata keys`() {
        logger.atInfo()
            .with(REPEATED_KEY, "foo")
            .with(REPEATED_KEY, "bar")
            .log("Several values")
        logger.atInfo()
            .with(FLAG_KEY)
            .log("Set Flag")
        logger.atInfo()
            .with(FLAG_KEY, false)
            .log("No flag")
        logger.atInfo()
            .with(REPEATED_KEY, "foo")
            .with(FLAG_KEY)
            .with(REPEATED_KEY, "bar")
            .log("...")

        backend.loggedCount shouldBe 4
        backend.logged[0].metadata.shouldContainInOrder(REPEATED_KEY, "foo", "bar")
        backend.logged[1].metadata.shouldUniquelyContain(FLAG_KEY, true)
        backend.logged[2].metadata.shouldUniquelyContain(FLAG_KEY, false)

        // Just check nothing weird happens when the metadata
        // is interleaved in the log statement.
        backend.logged[3].metadata.shouldContainInOrder(REPEATED_KEY, "foo", "bar")
        backend.logged[3].metadata.shouldUniquelyContain(FLAG_KEY, true)
    }

    /**
     * For testing log-site tags are correctly merged with metadata,
     * see [AbstractScopedLoggingContextTest][com.google.common.flogger.testing.AbstractScopedLoggingContextTest].
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
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val startNanos = MILLISECONDS.toNanos(currentTimeMillis())

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
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val startNanos = MILLISECONDS.toNanos(currentTimeMillis())

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
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val startNanos = MILLISECONDS.toNanos(currentTimeMillis())

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
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)
            val startNanos = MILLISECONDS.toNanos(currentTimeMillis())

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
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)
            val startNanos = MILLISECONDS.toNanos(currentTimeMillis())

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
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)

            // Logs for both types should appear.
            // Even though the 2nd log is within the rate limit period.
            // NOTE: It is important this is tested on a single log statement.
            var nowNanos = MILLISECONDS.toNanos(currentTimeMillis())
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
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)

            // Logs for both types should appear.
            // Even though the 2nd log is within the rate limit period.
            // NOTE: It is important this is tested on a single log statement.
            var nowNanos = MILLISECONDS.toNanos(currentTimeMillis())
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
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)

            // We can't test a specific implementation of `ScopedLoggingContext` here,
            // so we fake it. The `ScopedLoggingContext` behavior is well tested elsewhere.
            // Only tests should ever create “immediate providers” like this
            // as it doesn't make sense otherwise.
            var nowNanos = MILLISECONDS.toNanos(currentTimeMillis())
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
            val backend = FakeLoggerBackend()
            backend.setLevel(WARNING)
            val logger = TestLogger.create(backend)
            logger.forceAt(INFO).log("LOGGED")
            backend.loggedCount shouldBe 1
            backend.logged[0].shouldHaveMessage("LOGGED")
            backend.logged[0].metadata.shouldHaveSize(1)
            backend.logged[0].metadata.shouldUniquelyContain(Key.WAS_FORCED, true)
            backend.logged[0].wasForced().shouldBeTrue()
        }

        @Test
        fun `with 'every(n)' limiter`() {
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)
            val logSite = FakeLogSite.create("com.example.MyClass", "everyN", 123, null)

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
            val backend = FakeLoggerBackend()
            val logger = TestLogger.create(backend)
            val logSite = FakeLogSite.create("com.example.MyClass", "atMostEvery", 123, null)

            var nowNanos = MILLISECONDS.toNanos(currentTimeMillis())
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
        backend.lastLogged.shouldHaveArguments("foo", null, "baz")

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
        logger.atInfo().log("Hello %s World")
        backend.lastLogged.shouldHaveMessage("Hello %s World")
        backend.lastLogged.shouldHaveArguments()
    }

    @Test
    fun `handle a single argument`() {
        logger.atInfo().log("Hello %d World", 42)
        backend.lastLogged.shouldHaveMessage("Hello %d World")
        backend.lastLogged.shouldHaveArguments(42)
    }

    /**
     * Tests that a `null` literal is passed unmodified to the backend
     * without throwing an exception.
     */
    @Test
    fun `accept 'null' literal`() {
        // We want to call `log(String)`, not `log(Object)` with a null value.
        logger.atInfo().log(null as String?)
        backend.lastLogged.shouldHaveMessage(null)
    }

    /**
     * Tests that `null` arguments are passed unmodified to the backend
     * without throwing an exception.
     */
    @Test
    fun `accept 'null' argument`() {
        logger.atInfo().log("Hello %d World", null)
        backend.lastLogged.shouldHaveMessage("Hello %d World")
        backend.lastLogged.shouldHaveArguments(null)
    }

    /**
     * Currently having a `null` message and a `null` argument will throw a runtime exception,
     * but perhaps it shouldn't (it could come from data).
     *
     * In general, it is expected that when there are arguments to a log statement,
     * the message is a literal, which makes this situation very unlikely and probably
     * a code bug. But even then, throwing an exception is something that will only
     * happen when the log statement is enabled.
     *
     * Consider allowing this case to work without throwing a runtime exception.
     */
    @Test
    fun `throw when 'null' is passed for message and argument simultaneously`() {
        shouldThrow<NullPointerException> {
            logger.atInfo().log(null, null)
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
        logger.atSevere().withStackTrace(StackSize.FULL).log("Answer=%#x", 66)

        backend.loggedCount shouldBe 1
        backend.firstLogged.shouldHaveMessage("Answer=%#x")
        backend.firstLogged.shouldHaveArguments(66)
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
        println(actualStack)
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
            .log("Answer=%#x", 66)

        backend.loggedCount shouldBe 1
        backend.firstLogged.shouldHaveMessage("Answer=%#x")
        backend.firstLogged.shouldHaveArguments(66)
        backend.firstLogged.metadata.shouldHaveSize(1)
        backend.firstLogged.metadata.shouldContain(Key.LOG_CAUSE)

        val cause = backend.firstLogged.metadata.findValue(Key.LOG_CAUSE)!!
        cause shouldHaveMessage "SMALL"
        cause.stackTrace.size shouldBe StackSize.SMALL.maxDepth
        cause.cause shouldBe badness
    }

    @Test
    fun testExplicitLogSiteInjection() {
        // Tests it's the log site instance that controls rate limiting, even over different calls.
        // We don't expect this to ever happen in real code though.
        for (i in 0..6) {
            logHelper(
                logger, LogSites.logSite(), 2,
                "Foo: $i"
            ) // Log every 2nd (0, 2, 4, 6)
            logHelper(
                logger, LogSites.logSite(), 3,
                "Bar: $i"
            ) // Log every 3rd (0, 3, 6)
        }
        // Expect: Foo -> 0, 2, 4, 6 and Bar -> 0, 3, 6 (but not in that order)
        backend.loggedCount shouldBe 7
        backend.firstLogged.shouldHaveArguments("Foo: 0")
        backend.assertLogged(1).hasArguments("Bar: 0")
        backend.assertLogged(2).hasArguments("Foo: 2")
        backend.assertLogged(3).hasArguments("Bar: 3")
        backend.assertLogged(4).hasArguments("Foo: 4")
        backend.assertLogged(5).hasArguments("Foo: 6")
        backend.assertLogged(6).hasArguments("Bar: 6")
    }

    // It's important that injecting an INVALID log site acts as a override to suppress log site
    // calculation rather than being a no-op.
    @Test
    fun testExplicitLogSiteSuppression() {
        logger.atInfo().withInjectedLogSite(LogSite.INVALID).log("No log site here")
        logger.atInfo().withInjectedLogSite(null).log("No-op injection")
        backend.loggedCount shouldBe 2
        backend.firstLogged.logSite shouldBe LogSite.INVALID
        backend.assertLogged(1).logSite().isNotNull()
        backend.assertLogged(1).logSite().isNotEqualTo(LogSite.INVALID)
    }

    @Test
    fun testLogSiteSpecializationSameMetadata() {
        val fooMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
        fooKey shouldBe LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
    }

    @Test
    fun testLogSiteSpecializationKeyCountMatters() {
        val fooMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo")
        val repeatedMetadata = FakeMetadata()
            .add(Key.LOG_SITE_GROUPING_KEY, "foo")
            .add(Key.LOG_SITE_GROUPING_KEY, "foo")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
        val repeatedKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, repeatedMetadata)
        assertThat(fooKey).isNotEqualTo(repeatedKey)
    }

    @Test
    fun testLogSiteSpecializationDifferentKeys() {
        val fooMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo")
        val barMetadata = FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "bar")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
        val barKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barMetadata)
        assertThat(fooKey).isNotEqualTo(barKey)
    }

    // This is unfortunate but hard to work around unless SpecializedLogSiteKey can be made invariant
    // to the order of specialization (but this class must be very efficient, so that would be hard).
    // This should not be an issue in expected use, since specialization keys should always be applied
    // in the same order at any given log statement.
    @Test
    fun testLogSiteSpecializationOrderMatters() {
        val fooBarMetadata = FakeMetadata()
            .add(Key.LOG_SITE_GROUPING_KEY, "foo")
            .add(Key.LOG_SITE_GROUPING_KEY, "bar")
        val barFooMetadata = FakeMetadata()
            .add(Key.LOG_SITE_GROUPING_KEY, "bar")
            .add(Key.LOG_SITE_GROUPING_KEY, "foo")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooBarKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooBarMetadata)
        val barFooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barFooMetadata)
        assertThat(fooBarKey).isNotEqualTo(barFooKey)
    }

    @Test
    fun testLogSiteSpecializationKey() {
        Key.LOG_SITE_GROUPING_KEY.emitRepeated(
            Iterators.forArray<Any>("foo")
        ) { k: String?, v: Any? ->
            k shouldBe "group_by"
            v shouldBe "foo"
        }

        // We don't care too much about the case with multiple keys since it's so rare, but it should
        // be vaguely sensible.
        Key.LOG_SITE_GROUPING_KEY.emitRepeated(
            Iterators.forArray<Any>("foo", "bar")
        ) { k: String?, v: Any? ->
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

private enum class LogType {
    FOO,
    BAR
}
