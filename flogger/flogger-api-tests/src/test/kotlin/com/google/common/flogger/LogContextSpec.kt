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

import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterators
import com.google.common.collect.Range
import com.google.common.flogger.context.Tags
import com.google.common.flogger.testing.FakeLogSite
import com.google.common.flogger.testing.FakeLoggerBackend
import com.google.common.flogger.testing.FakeMetadata
import com.google.common.flogger.testing.TestLogger
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import io.kotest.assertions.throwables.shouldThrow
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogContext` should")
internal class LogContextSpec {

    @Test
    fun testIsEnabled() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        backend.setLevel(Level.INFO)
        assertThat(logger.atFine().isEnabled()).isFalse()
        assertThat(logger.atInfo().isEnabled()).isTrue()
        assertThat(logger.at(Level.WARNING).isEnabled()).isTrue()
    }

    @Test
    fun testLoggingWithCause() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val cause = Throwable()
        logger.atInfo().withCause(cause).log("Hello World")
        backend.assertLastLogged().metadata().hasSize(1)
        backend.assertLastLogged().metadata().containsUniqueEntry(LogContext.Key.LOG_CAUSE, cause)
        backend.assertLastLogged().hasMessage("Hello World")
    }

    @Test
    fun testLazyArgs() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.atInfo().log("Hello %s", LazyArgs.lazy { "World" })
        logger.atFine().log(
            "Hello %s",
            LazyArgs.lazy<Any> {
                throw RuntimeException(
                    "Lazy arguments should not be evaluated in a disabled log statement"
                )
            })

        // By the time the backend processes a log statement, lazy arguments have been evaluated.
        backend.assertLastLogged().hasMessage("Hello %s")
        backend.assertLastLogged().hasArguments("World")
    }

    @Test
    fun testFormattedMessage() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.at(Level.INFO).log("Formatted %s", "Message")
        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLastLogged().hasMessage("Formatted %s")
        backend.assertLastLogged().hasArguments("Message")

        // Cannot ask for literal argument as none exists.
        shouldThrow<IllegalStateException> {
            backend.getLogged(0).getLiteralArgument()
        }
    }

    @Test
    fun testLiteralMessage() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.at(Level.INFO).log("Literal Message")
        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLastLogged().hasMessage("Literal Message")

        // Cannot ask for format arguments as none exist.
        assertThat(backend.getLogged(0).getTemplateContext()).isNull()
        shouldThrow<IllegalStateException> {
            backend.getLogged(0).getArguments()
        }
    }

    @Test
    fun testMultipleMetadata() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val cause: Exception = RuntimeException()
        logger.atInfo().withCause(cause).every(42).log("Hello World")
        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLogged(0).metadata().hasSize(2)
        backend.assertLogged(0).metadata().containsUniqueEntry(LogContext.Key.LOG_EVERY_N, 42)
        backend.assertLogged(0).metadata().containsUniqueEntry(LogContext.Key.LOG_CAUSE, cause)
    }

    @Test
    fun testMetadataKeys() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.atInfo().with(REPEATED_KEY, "foo").with(REPEATED_KEY, "bar").log("Several values")
        logger.atInfo().with(FLAG_KEY).log("Set Flag")
        logger.atInfo().with(FLAG_KEY, false).log("No flag")
        logger.atInfo().with(REPEATED_KEY, "foo").with(FLAG_KEY).with(REPEATED_KEY, "bar")
            .log("...")
        assertThat(backend.loggedCount).isEqualTo(4)
        backend.assertLogged(0).metadata().containsEntries(REPEATED_KEY, "foo", "bar")
        backend.assertLogged(1).metadata().containsUniqueEntry(FLAG_KEY, true)
        backend.assertLogged(2).metadata().containsUniqueEntry(FLAG_KEY, false)
        // Just check nothing weird happens when the metadata is interleaved in the log statement.
        backend.assertLogged(3).metadata().containsEntries(REPEATED_KEY, "foo", "bar")
        backend.assertLogged(3).metadata().containsUniqueEntry(FLAG_KEY, true)
    }

    // For testing that log-site tags are correctly merged with metadata, see
    // AbstractScopedLoggingContextTest.
    @Test
    fun testLoggedTags() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val tags = Tags.of("foo", "bar")
        logger.atInfo().with(LogContext.Key.TAGS, tags).log("With tags")
        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLogged(0).metadata().containsUniqueEntry(LogContext.Key.TAGS, tags)
    }

    @Test
    fun testEveryN() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val startNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        // Logging occurs for counts: 0, 5, 10 (timestamp is not important).
        var millis = 0
        var count = 0
        while (millis <= 1000) {
            val timestampNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(millis.toLong())
            logger.at(Level.INFO, timestampNanos).every(5).log("Count=%d", count++)
            millis += 100
        }
        assertThat(backend.loggedCount).isEqualTo(3)
        backend.assertLogged(0).metadata().containsUniqueEntry(LogContext.Key.LOG_EVERY_N, 5)
        // Check the first log we captured was the first one emitted.
        backend.assertLogged(0).timestampNanos().isEqualTo(startNanos)

        // Check the expected count and skipped-count for each log.
        backend.assertLogged(0).hasArguments(0)
        backend.assertLogged(0).metadata().keys().doesNotContain(LogContext.Key.SKIPPED_LOG_COUNT)
        backend.assertLogged(1).hasArguments(5)
        backend.assertLogged(1).metadata().containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, 4)
        backend.assertLogged(2).hasArguments(10)
        backend.assertLogged(2).metadata().containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, 4)
    }

    @Test
    fun testOnAverageEveryN() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val startNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        // Logging occurs randomly 1-in-5 times over 1000 log statements.
        var millis = 0
        var count = 0
        while (millis <= 1000) {
            val timestampNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(millis.toLong())
            logger.at(Level.INFO, timestampNanos).onAverageEvery(5).log("Count=%d", count++)
            millis += 1
        }

        // Satisically impossible that we randomly get +/- 100 over 1000 logs.
        assertThat(backend.loggedCount).isIn(Range.closed(100, 300))
        backend.assertLogged(0).metadata().containsUniqueEntry(LogContext.Key.LOG_SAMPLE_EVERY_N, 5)

        // Check the expected count and skipped-count for each log based on the timestamp.
        var lastLogIndex = -1
        for (n in 0..<backend.loggedCount) {
            // The timestamp increases by 1 millisecond each time so we can get the log index from it.
            val deltaNanos = backend.getLogged(n).getTimestampNanos() - startNanos
            val logIndex = (deltaNanos / TimeUnit.MILLISECONDS.toNanos(1)).toInt()
            backend.assertLogged(n).hasArguments(logIndex)
            // This works even if lastLogIndex == -1.
            val skipped = logIndex - lastLogIndex - 1
            if (skipped == 0) {
                backend.assertLogged(n).metadata().keys()
                    .doesNotContain(LogContext.Key.SKIPPED_LOG_COUNT)
            } else {
                backend.assertLogged(n).metadata()
                    .containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, skipped)
            }
            lastLogIndex = logIndex
        }
    }

    @Test
    fun testAtMostEvery() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)

        // Logging occurs at: +0ms, +2400ms, +4800ms
        // Note it will not occur at 4200ms (which is the first logging attempt after the
        // 2nd multiple of 2 seconds because the timestamp is reset to be (start + 2400ms)
        // and not (start + 2000ms). atMostEvery() does not rate limit over multiple samples.
        val startNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        var millis = 0
        var count = 0
        while (millis <= 5000) {
            val timestampNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(millis.toLong())
            logger.at(Level.INFO, timestampNanos).atMostEvery(2, TimeUnit.SECONDS)
                .log("Count=%d", count++)
            millis += 600
        }
        assertThat(backend.loggedCount).isEqualTo(3)
        val rateLimit = DurationRateLimiter.newRateLimitPeriod(2, TimeUnit.SECONDS)
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, rateLimit)
        // Check the first log we captured was the first one emitted.
        backend.assertLogged(0).timestampNanos().isEqualTo(startNanos)

        // Check the expected count and skipped-count for each log.
        backend.assertLogged(0).hasArguments(0)
        backend.assertLogged(0).metadata().keys().doesNotContain(LogContext.Key.SKIPPED_LOG_COUNT)
        backend.assertLogged(1).hasArguments(4)
        backend.assertLogged(1).metadata().containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, 3)
        backend.assertLogged(2).hasArguments(8)
        backend.assertLogged(2).metadata().containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, 3)
    }

    @Test
    fun testMultipleRateLimiters_higherLoggingRate() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)

        // 10 logs per second over 6 seconds.
        val startNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        var millis = 0
        var count = 0
        while (millis <= 6000) {
            val timestampNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(millis.toLong())
            // More than N logs occur per rate limit period, so logging should occur every 2 seconds.
            logger.at(Level.INFO, timestampNanos).every(15).atMostEvery(2, TimeUnit.SECONDS)
                .log("Count=%d", count++)
            millis += 100
        }
        assertThat(backend.loggedCount).isEqualTo(4)
        backend.assertLogged(0).hasArguments(0)
        backend.assertLogged(1).hasArguments(20)
        backend.assertLogged(2).hasArguments(40)
        backend.assertLogged(3).hasArguments(60)
        backend.assertLogged(3).metadata().containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, 19)
    }

    @Test
    fun testMultipleRateLimiters_lowerLoggingRate() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)

        // 10 logs per second over 6 seconds.
        val startNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        var millis = 0
        var count = 0
        while (millis <= 6000) {
            val timestampNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(millis.toLong())
            // Fever than N logs occur in the rate limit period, so logging should occur every 15 logs.
            logger.at(Level.INFO, timestampNanos).every(15).atMostEvery(1, TimeUnit.SECONDS)
                .log("Count=%d", count++)
            millis += 100
        }
        assertThat(backend.loggedCount).isEqualTo(5)
        backend.assertLogged(0).hasArguments(0)
        backend.assertLogged(1).hasArguments(15)
        backend.assertLogged(2).hasArguments(30)
        backend.assertLogged(3).hasArguments(45)
        backend.assertLogged(4).hasArguments(60)
        backend.assertLogged(4).metadata().containsUniqueEntry(LogContext.Key.SKIPPED_LOG_COUNT, 14)
    }

    @Test
    fun testPer_withStrategy() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)

        // Logs for both types should appear (even though the 2nd log is within the rate limit period).
        // NOTE: It is important this is tested on a single log statement.
        var nowNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        for (err in listOf(
            IllegalArgumentException(),
            NullPointerException(),
            NullPointerException(),
            IllegalArgumentException()
        )) {
            logger
                .at(Level.INFO, nowNanos)
                .atMostEvery(1, TimeUnit.SECONDS)
                .per(err, LogPerBucketingStrategy.byClass())
                .log("Err: %s", err.message)
            nowNanos += TimeUnit.MILLISECONDS.toNanos(100)
        }
        assertThat(backend.loggedCount).isEqualTo(2)

        // Rate limit period and the aggregation key from "per"
        backend.assertLogged(0).metadata().hasSize(2)
        backend
            .assertLogged(0)
            .metadata()
            .containsUniqueEntry(
                LogContext.Key.LOG_SITE_GROUPING_KEY,
                IllegalArgumentException::class.java
            )
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
        backend.assertLogged(1).metadata().hasSize(2)
        backend
            .assertLogged(1)
            .metadata()
            .containsUniqueEntry(
                LogContext.Key.LOG_SITE_GROUPING_KEY,
                NullPointerException::class.java
            )
        backend.assertLogged(1).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
    }

    // Non-private to allow static import to keep test code concise.
    internal enum class LogType {
        FOO,
        BAR
    }

    @Test
    fun testPer_enum() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)

        // Logs for both types should appear (even though the 2nd log is within the rate limit period).
        // NOTE: It is important this is tested on a single log statement.
        var nowNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        for (type in listOf(
            LogType.FOO,
            LogType.FOO,
            LogType.FOO,
            LogType.BAR,
            LogType.FOO,
            LogType.BAR,
            LogType.FOO
        )) {
            logger.at(Level.INFO, nowNanos).atMostEvery(1, TimeUnit.SECONDS).per(type)
                .log("Type: %s", type)
            nowNanos += TimeUnit.MILLISECONDS.toNanos(100)
        }
        assertThat(backend.loggedCount).isEqualTo(2)

        // Rate limit period and the aggregation key from "per"
        backend.assertLogged(0).hasArguments(LogType.FOO)
        backend.assertLogged(0).metadata().hasSize(2)
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_SITE_GROUPING_KEY, LogType.FOO)
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
        backend.assertLogged(1).hasArguments(LogType.BAR)
        backend.assertLogged(1).metadata().hasSize(2)
        backend.assertLogged(1).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_SITE_GROUPING_KEY, LogType.BAR)
        backend.assertLogged(1).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
    }

    @Test
    fun testPer_scopeProvider() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)

        // We can't test a specific implementation of ScopedLoggingContext here (there might not be one
        // available), so we fake it. The ScopedLoggingContext behaviour is well tested elsewhere. Only
        // tests should ever create "immediate providers" like this as it doesn't make sense otherwise.
        val fooScope = LoggingScope.create("foo")
        val barScope = LoggingScope.create("bar")
        val foo = LoggingScopeProvider { fooScope }
        val bar = LoggingScopeProvider { barScope }

        // Logs for both scopes should appear (even though the 2nd log is within the rate limit period).
        // NOTE: It is important this is tested on a single log statement.
        var nowNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        for (sp in listOf(foo, foo, foo, bar, foo, bar, foo)) {
            logger.at(Level.INFO, nowNanos).atMostEvery(1, TimeUnit.SECONDS).per(sp).log("message")
            nowNanos += TimeUnit.MILLISECONDS.toNanos(100)
        }
        assertThat(backend.loggedCount).isEqualTo(2)

        // Rate limit period and the aggregation key from "per"
        backend.assertLogged(0).metadata().hasSize(2)
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_SITE_GROUPING_KEY, fooScope)
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
        backend.assertLogged(1).metadata().hasSize(2)
        backend.assertLogged(1).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_SITE_GROUPING_KEY, barScope)
        backend.assertLogged(1).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
    }

    @Test
    fun testWasForced_level() {
        val backend = FakeLoggerBackend()
        backend.setLevel(Level.WARNING)
        val logger = TestLogger.create(backend)
        logger.forceAt(Level.INFO).log("LOGGED")
        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLogged(0).hasMessage("LOGGED")
        backend.assertLogged(0).metadata().hasSize(1)
        backend.assertLogged(0).metadata().containsUniqueEntry(LogContext.Key.WAS_FORCED, true)
        backend.assertLogged(0).wasForced()
    }

    @Test
    fun testWasForced_everyN() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val logSite = FakeLogSite.create("com.example.MyClass", "everyN", 123, null)

        // Log statements always get logged the first time.
        logger.atInfo().every(3).withInjectedLogSite(logSite).log("LOGGED 1")
        logger.atInfo().every(3).withInjectedLogSite(logSite).log("NOT LOGGED")
        // Manually create the forced context (there is no "normal" api for this).
        logger.forceAt(Level.INFO).every(3).withInjectedLogSite(logSite).log("LOGGED 2")
        // This shows that the "forced" context does not count towards the rate limit count (otherwise
        // this log statement would have been logged).
        logger.atInfo().every(3).withInjectedLogSite(logSite).log("NOT LOGGED")
        assertThat(backend.loggedCount).isEqualTo(2)
        backend.assertLogged(0).hasMessage("LOGGED 1")
        // No rate limit metadata was added, but it was marked as forced.
        backend.assertLogged(1).hasMessage("LOGGED 2")
        backend.assertLogged(1).metadata().hasSize(1)
        backend.assertLogged(1).metadata().containsUniqueEntry(LogContext.Key.WAS_FORCED, true)
    }

    @Test
    fun testWasForced_atMostEvery() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val logSite = FakeLogSite.create("com.example.MyClass", "atMostEvery", 123, null)

        // Log statements always get logged the first time.
        var nowNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        logger.at(Level.INFO, nowNanos).atMostEvery(1, TimeUnit.SECONDS)
            .withInjectedLogSite(logSite).log("LOGGED 1")
        nowNanos += TimeUnit.MILLISECONDS.toNanos(100)
        logger
            .at(Level.INFO, nowNanos)
            .atMostEvery(1, TimeUnit.SECONDS)
            .withInjectedLogSite(logSite)
            .log("NOT LOGGED")
        nowNanos += TimeUnit.MILLISECONDS.toNanos(100)
        logger
            .forceAt(Level.INFO, nowNanos)
            .atMostEvery(1, TimeUnit.SECONDS)
            .withInjectedLogSite(logSite)
            .log("LOGGED 2")
        nowNanos += TimeUnit.MILLISECONDS.toNanos(100)
        logger
            .at(Level.INFO, nowNanos)
            .atMostEvery(1, TimeUnit.SECONDS)
            .withInjectedLogSite(logSite)
            .log("NOT LOGGED")
        assertThat(backend.loggedCount).isEqualTo(2)
        backend.assertLogged(0).hasMessage("LOGGED 1")
        backend.assertLogged(0).metadata().hasSize(1)
        backend.assertLogged(0).metadata()
            .containsUniqueEntry(LogContext.Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND)
        backend.assertLogged(1).hasMessage("LOGGED 2")
        backend.assertLogged(1).metadata().hasSize(1)
        backend.assertLogged(1).metadata().containsUniqueEntry(LogContext.Key.WAS_FORCED, true)
    }

    // These tests verify that the mapping between the logging context and the backend preserves
    // arguments as expected.
    @Test
    fun testExplicitVarargs() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val args = arrayOf<Any?>("foo", null, "baz")
        logger.atInfo().logVarargs("Any message ...", args)
        backend.assertLastLogged().hasArguments("foo", null, "baz")
        // Make sure we took a copy of the arguments rather than risk re-using them.
        assertThat(backend.loggedCount).isEqualTo(1)
        assertThat(backend.getLogged(0).getArguments()).isNotSameInstanceAs(args)
    }

    @Test
    fun testNoArguments() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)

        // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
        logger.atInfo().log()
        backend.assertLastLogged().hasMessage("")
        backend.assertLastLogged().hasArguments()
    }

    @Test
    fun testLiteralArgument_doesNotEscapePercent() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.atInfo().log("Hello %s World")
        backend.assertLastLogged().hasMessage("Hello %s World")
        backend.assertLastLogged().hasArguments()
    }

    @Test
    fun testSingleParameter() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.atInfo().log("Hello %d World", 42)
        backend.assertLastLogged().hasMessage("Hello %d World")
        backend.assertLastLogged().hasArguments(42)
    }

    // Tests that a null literal is passed unmodified to the backend without throwing an exception.
    @Test
    fun testNullLiteral() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        // We want to call log(String) (not log(Object)) with a null value.
        logger.atInfo().log(null as String?)
        backend.assertLastLogged().hasMessage(null)
    }

    // Tests that null arguments are passed unmodified to the backend without throwing an exception.
    @Test
    fun testNullArgument() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.atInfo().log("Hello %d World", null)
        backend.assertLastLogged().hasMessage("Hello %d World")
        backend.assertLastLogged().hasArguments(*arrayOf(null))
    }

    // Currently having a null message and a null argument will throw a runtime exception, but
    // perhaps it shouldn't (it could come from data). In general it is expected that when there are
    // arguments to a log statement the message is a literal, which makes this situation very
    // unlikely and probably a code bug (but even then throwing an exception is something that will
    // only happen when the log statement is enabled).
    // TODO(dbeaumont): Consider allowing this case to work without throwing a runtime exception.
    @Test
    fun testNullMessageAndArgument() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        shouldThrow<NullPointerException> {
            logger.atInfo().log(null, null)
        }
    }

    @Test
    fun testManyObjectParameters() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val ms = "Any message will do..."

        // Verify that the arguments passed in to the Object based methods are mapped correctly.
        logger.atInfo().log(ms, "1")
        backend.assertLastLogged().hasArguments("1")
        logger.atInfo().log(ms, "1", "2")
        backend.assertLastLogged().hasArguments("1", "2")
        logger.atInfo().log(ms, "1", "2", "3")
        backend.assertLastLogged().hasArguments("1", "2", "3")
        logger.atInfo().log(ms, "1", "2", "3", "4")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7", "8")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
        backend
            .assertLastLogged()
            .hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
        logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
        backend
            .assertLastLogged()
            .hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
    }

    @Test
    fun testOneUnboxedArgument() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val ms = "Any message will do..."

        // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
        logger.atInfo().log(ms, BYTE_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG)
        logger.atInfo().log(ms, SHORT_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG)
        logger.atInfo().log(ms, INT_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG)
        logger.atInfo().log(ms, LONG_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG)
        logger.atInfo().log(ms, CHAR_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG)
    }

    @Test
    fun testTwoUnboxedArguments() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val ms = "Any message will do..."

        // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
        logger.atInfo().log(ms, BYTE_ARG, BYTE_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG, BYTE_ARG)
        logger.atInfo().log(ms, BYTE_ARG, SHORT_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG, SHORT_ARG)
        logger.atInfo().log(ms, BYTE_ARG, INT_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG, INT_ARG)
        logger.atInfo().log(ms, BYTE_ARG, LONG_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG, LONG_ARG)
        logger.atInfo().log(ms, BYTE_ARG, CHAR_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG, CHAR_ARG)
        logger.atInfo().log(ms, SHORT_ARG, BYTE_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG, BYTE_ARG)
        logger.atInfo().log(ms, SHORT_ARG, SHORT_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG, SHORT_ARG)
        logger.atInfo().log(ms, SHORT_ARG, INT_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG, INT_ARG)
        logger.atInfo().log(ms, SHORT_ARG, LONG_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG, LONG_ARG)
        logger.atInfo().log(ms, SHORT_ARG, CHAR_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG, CHAR_ARG)
        logger.atInfo().log(ms, INT_ARG, BYTE_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG, BYTE_ARG)
        logger.atInfo().log(ms, INT_ARG, SHORT_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG, SHORT_ARG)
        logger.atInfo().log(ms, INT_ARG, INT_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG, INT_ARG)
        logger.atInfo().log(ms, INT_ARG, LONG_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG, LONG_ARG)
        logger.atInfo().log(ms, INT_ARG, CHAR_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG, CHAR_ARG)
        logger.atInfo().log(ms, LONG_ARG, BYTE_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG, BYTE_ARG)
        logger.atInfo().log(ms, LONG_ARG, SHORT_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG, SHORT_ARG)
        logger.atInfo().log(ms, LONG_ARG, INT_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG, INT_ARG)
        logger.atInfo().log(ms, LONG_ARG, LONG_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG, LONG_ARG)
        logger.atInfo().log(ms, LONG_ARG, CHAR_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG, CHAR_ARG)
        logger.atInfo().log(ms, CHAR_ARG, BYTE_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG, BYTE_ARG)
        logger.atInfo().log(ms, CHAR_ARG, SHORT_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG, SHORT_ARG)
        logger.atInfo().log(ms, CHAR_ARG, INT_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG, INT_ARG)
        logger.atInfo().log(ms, CHAR_ARG, LONG_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG, LONG_ARG)
        logger.atInfo().log(ms, CHAR_ARG, CHAR_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG, CHAR_ARG)
    }

    @Test
    fun testTwoMixedArguments() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val ms = "Any message will do..."

        // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
        logger.atInfo().log(ms, OBJECT_ARG, BYTE_ARG)
        backend.assertLastLogged().hasArguments(OBJECT_ARG, BYTE_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, SHORT_ARG)
        backend.assertLastLogged().hasArguments(OBJECT_ARG, SHORT_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, INT_ARG)
        backend.assertLastLogged().hasArguments(OBJECT_ARG, INT_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, LONG_ARG)
        backend.assertLastLogged().hasArguments(OBJECT_ARG, LONG_ARG)
        logger.atInfo().log(ms, OBJECT_ARG, CHAR_ARG)
        backend.assertLastLogged().hasArguments(OBJECT_ARG, CHAR_ARG)
        logger.atInfo().log(ms, BYTE_ARG, OBJECT_ARG)
        backend.assertLastLogged().hasArguments(BYTE_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, SHORT_ARG, OBJECT_ARG)
        backend.assertLastLogged().hasArguments(SHORT_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, INT_ARG, OBJECT_ARG)
        backend.assertLastLogged().hasArguments(INT_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, LONG_ARG, OBJECT_ARG)
        backend.assertLastLogged().hasArguments(LONG_ARG, OBJECT_ARG)
        logger.atInfo().log(ms, CHAR_ARG, OBJECT_ARG)
        backend.assertLastLogged().hasArguments(CHAR_ARG, OBJECT_ARG)
    }

    @Test
    fun testWithStackTrace() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)

        // Keep these 2 lines immediately adjacent to each other.
        val expectedCaller = callerInfoFollowingLine()
        logger.atSevere().withStackTrace(StackSize.FULL).log("Answer=%#x", 66)

        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLogged(0).hasMessage("Answer=%#x")
        backend.assertLogged(0).hasArguments(66)
        backend.assertLogged(0).metadata().hasSize(1)
        backend.assertLogged(0).metadata().keys().contains(LogContext.Key.LOG_CAUSE)
        val cause = backend.getLogged(0).getMetadata().findValue(LogContext.Key.LOG_CAUSE)
        assertThat(cause).hasMessageThat().isEqualTo("FULL")
        assertThat(cause!!.cause).isNull()
        val actualStack = listOf(*cause.stackTrace)
        val expectedStack = mutableListOf(*Throwable().stackTrace)
        // Overwrite the first element to the expected value.
        expectedStack[0] = expectedCaller
        // Use string representation for comparison since synthetic stack elements are not "equal" to
        // equivalent system stack elements.

        println(actualStack)
        println()
        println()
        println()
        println(expectedStack)

        assertThat(actualStack)
            .comparingElementsUsing(
                Correspondence.transforming(
                    { obj: Any? -> obj.toString() },
                    { obj: Any? -> obj.toString() }, "toString"
                )
            )
            .containsExactlyElementsIn(expectedStack)
            .inOrder()
    }

    @Test
    fun testWithStackTraceAndCause() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        val badness = RuntimeException("badness")

        // Use "SMALL" size here because we rely on the total stack depth in this test being bigger
        // than that. Using "MEDIUM" or "LARGE" might cause the test to fail when verifying the
        // truncated stack size.
        logger.atInfo().withStackTrace(StackSize.SMALL).withCause(badness).log("Answer=%#x", 66)
        assertThat(backend.loggedCount).isEqualTo(1)
        backend.assertLogged(0).hasMessage("Answer=%#x")
        backend.assertLogged(0).hasArguments(66)
        backend.assertLogged(0).metadata().hasSize(1)
        backend.assertLogged(0).metadata().keys().contains(LogContext.Key.LOG_CAUSE)
        val cause = backend.getLogged(0).getMetadata().findValue(LogContext.Key.LOG_CAUSE)
        assertThat(cause).hasMessageThat().isEqualTo("SMALL")
        assertThat(cause!!.stackTrace.size).isEqualTo(StackSize.SMALL.maxDepth)
        assertThat(cause.cause).isEqualTo(badness)
    }

    // See b/27310448.
    @Test
    fun testStackTraceFormatting() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)

        // Keep these 2 lines immediately adjacent to each other.
        val expectedCaller = callerInfoFollowingLine()
        logger.atWarning().withStackTrace(StackSize.MEDIUM).log("Message")

        // Print the stack trace via the expected method (ie, printStackTrace()).
        val cause = backend.getLogged(0).getMetadata().findValue(LogContext.Key.LOG_CAUSE)
        assertThat(cause).hasMessageThat().isEqualTo("MEDIUM")
        val out = StringWriter()
        cause!!.printStackTrace(PrintWriter(out))
        val stackLines = Splitter.on('\n').trimResults().splitToList(out.toString())
        val actualStackRefs =
            stackLines.stream() // Ignore lines that don't look like call-stack entries.
                .filter { s: String -> s.startsWith("at ") } // Remove anything that's not caller information.
                .map { s: String ->
                    s.replace(
                        "^at (?:java\\.base/)?".toRegex(),
                        ""
                    )
                }
                .collect(ImmutableList.toImmutableList())

        // We assume there's at least one element in the stack we're testing.
        assertThat(actualStackRefs).isNotEmpty()
        val expectedElements = Throwable().stackTrace
        // Overwrite first element since we are starting from a different place (in the same method).
        expectedElements[0] = expectedCaller
        // Mimic the standard formatting for stack traces (a bit fragile but at least it's explicit).
        val expectedStackRefs: List<String> =
            Arrays.stream(expectedElements) // Format the elements into something that should match the normal stack formatting.
                // Apologies to whoever has to debug/fix this if it ever breaks :(
                // Native methods (where line number < 0) are formatted differently.
                .map { e: StackTraceElement ->
                    if (e.lineNumber >= 0) String.format(
                        "%s.%s(%s:%d)",
                        e.className, e.methodName, e.fileName, e.lineNumber
                    ) else String.format(
                        "%s.%s(Native Method)", e.className, e.methodName
                    )
                } // Limit to the number in the synthetic stack trace (which is truncated).
                .limit(actualStackRefs.size.toLong())
                .collect(ImmutableList.toImmutableList())

        // This doesn't check anything about the message that's printed before the stack lines,
        // but that's not the point of this test.
        assertThat(actualStackRefs).isEqualTo(expectedStackRefs)
    }

    @Test
    fun testExplicitLogSiteInjection() {
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
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
        assertThat(backend.loggedCount).isEqualTo(7)
        backend.assertLogged(0).hasArguments("Foo: 0")
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
        val backend = FakeLoggerBackend()
        val logger = FluentLogger2(backend)
        logger.atInfo().withInjectedLogSite(LogSite.INVALID).log("No log site here")
        logger.atInfo().withInjectedLogSite(null).log("No-op injection")
        assertThat(backend.loggedCount).isEqualTo(2)
        backend.assertLogged(0).logSite().isEqualTo(LogSite.INVALID)
        backend.assertLogged(1).logSite().isNotNull()
        backend.assertLogged(1).logSite().isNotEqualTo(LogSite.INVALID)
    }

    @Test
    fun testLogSiteSpecializationSameMetadata() {
        val fooMetadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
        assertThat(fooKey)
            .isEqualTo(LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata))
    }

    @Test
    fun testLogSiteSpecializationKeyCountMatters() {
        val fooMetadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
        val repeatedMetadata = FakeMetadata()
            .add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
            .add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
        val repeatedKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, repeatedMetadata)
        assertThat(fooKey).isNotEqualTo(repeatedKey)
    }

    @Test
    fun testLogSiteSpecializationDifferentKeys() {
        val fooMetadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
        val barMetadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, "bar")
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
            .add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
            .add(LogContext.Key.LOG_SITE_GROUPING_KEY, "bar")
        val barFooMetadata = FakeMetadata()
            .add(LogContext.Key.LOG_SITE_GROUPING_KEY, "bar")
            .add(LogContext.Key.LOG_SITE_GROUPING_KEY, "foo")
        val logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooBarKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooBarMetadata)
        val barFooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barFooMetadata)
        assertThat(fooBarKey).isNotEqualTo(barFooKey)
    }

    @Test
    fun testLogSiteSpecializationKey() {
        LogContext.Key.LOG_SITE_GROUPING_KEY.emitRepeated(
            Iterators.forArray<Any>("foo")
        ) { k: String?, v: Any? ->
            assertThat(k).isEqualTo("group_by")
            assertThat(v).isEqualTo("foo")
        }

        // We don't care too much about the case with multiple keys since it's so rare, but it should
        // be vaguely sensible.
        LogContext.Key.LOG_SITE_GROUPING_KEY.emitRepeated(
            Iterators.forArray<Any>("foo", "bar")
        ) { k: String?, v: Any? ->
            assertThat(k).isEqualTo("group_by")
            assertThat(v).isEqualTo("[foo,bar]")
        }
    }

    companion object {
        // Arbitrary constants of overloaded types for testing argument mappings.
        private const val BYTE_ARG = Byte.MAX_VALUE
        private const val SHORT_ARG = Short.MAX_VALUE
        private const val INT_ARG = Int.MAX_VALUE
        private const val LONG_ARG = Long.MAX_VALUE
        private const val CHAR_ARG = 'X'
        private val OBJECT_ARG = Any()
        private val REPEATED_KEY = MetadataKey.repeated("str", String::class.java)
        private val FLAG_KEY = MetadataKey.repeated("flag", Boolean::class.javaObjectType)
        private val ONCE_PER_SECOND = DurationRateLimiter.newRateLimitPeriod(1, TimeUnit.SECONDS)

        // In normal use, the logger would never need to be passed in and you'd use logVarargs().
        private fun logHelper(logger: FluentLogger2, logSite: LogSite, n: Int, message: String) {
            logger.atInfo().withInjectedLogSite(logSite).every(n).log("%s", message)
        }
    }
}

/**
 * Returns a [StackTraceElement] with the incremented line number.
 */
private fun callerInfoFollowingLine(): StackTraceElement {
    // We reference third element due to Kotlin-generated intermediate Java classes.
    val caller = Exception().stackTrace[2]
    return StackTraceElement(
        caller.className,
        caller.methodName,
        caller.fileName,
        caller.lineNumber + 1
    )
}
