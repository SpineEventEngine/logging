/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.context

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.jvm.LogContext.Key
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.backend.Platform
import io.spine.logging.jvm.backend.given.MemoizingLoggerBackend
import io.spine.logging.jvm.backend.given.shouldBeEmpty
import io.spine.logging.jvm.backend.given.shouldContainInOrder
import io.spine.logging.jvm.backend.given.shouldHaveSize
import io.spine.logging.jvm.backend.given.shouldUniquelyContain
import io.spine.logging.jvm.given.ConfigurableLogger
import io.spine.logging.jvm.repeatedKey
import io.spine.logging.jvm.singleKey
import io.spine.logging.toJavaLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/* These types clash with simple class names in this package. */
import io.spine.logging.Level as TLevel
import io.spine.logging.context.LogLevelMap as TLogLevelMap
import io.spine.logging.context.ScopedLoggingContext as TScopedLoggingContext
import java.util.logging.Level as JLevel

private typealias LoggerName = String

/**
 * Set of common tests for [ContextDataProvider]s.
 *
 * @see <a href="https://rb.gy/luq6y">Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`ContextDataProvider` should") // This name is to be overridden by inheritors.
abstract class AbstractContextDataProviderSpec {

    private lateinit var contextData: ContextDataProvider
    private lateinit var context: ScopedLoggingContext

    private val contextTags: Map<String, Set<Any?>>
        get() = contextData.getTags().asMap()

    private val contextMetadata: Metadata
        get() = contextData.getMetadata()

    /**
     * A flag to be set inside an innermost callback to prove it was executed.
     */
    private var callbackWasExecuted = false

    /**
     * Returns the tested [ContextDataProvider].
     */
    protected abstract val implementationUnderTest: ContextDataProvider

    private companion object {
        private val FOO = singleKey<String>("foo")
        private val BAR = repeatedKey<String>("bar")
        private val SUB_TASK = ScopeType.create("sub task")
        private val BATCH_JOB = ScopeType.create("batch job")
    }

    /**
     * Specifies an actual implementation as a test subject.
     *
     * These properties are not initialized during declaration because open
     * members should not be used during an instance initialization.
     * Such is reported as a compiler warning.
     */
    @BeforeEach
    fun setImplementation() {
        contextData = implementationUnderTest
        context = contextData.getContextApiSingleton()
    }

    /**
     * Checks that an innermost callback within a test was executed.
     *
     * To make this method work, call [markCallbackExecuted] at the end
     * of the innermost callback.
     *
     * Do not use `@AfterEach` here because the subclass may not use this in
     * its own tests. Just put it at the end of tests with a nested callback.
     */
    private fun checkCallbackWasExecuted() {
        callbackWasExecuted.shouldBeTrue()
    }

    /**
     * Sets [callbackWasExecuted] to `true`.
     */
    private fun markCallbackExecuted() {
        callbackWasExecuted = true
    }

    @Suppress("unused") // False positive from IDEA.
    @Nested inner class
    `create a new context` {

        @Test
        fun `with tags`() {
            val tags = Tags.of("foo", "bar")
            contextTags.shouldBeEmpty()
            context.newContext()
                .withTags(tags)
                .run {
                    contextTags shouldBe tags.asMap()
                    markCallbackExecuted()
                }
            contextTags.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        @Test
        fun `with metadata`() {
            val (key, value) = FOO to "foo"
            contextMetadata.shouldBeEmpty()
            context.newContext()
                .withMetadata(key, value)
                .run {
                    // Should be unique because the key is singleton.
                    contextMetadata.shouldUniquelyContain(key, value)
                    markCallbackExecuted()
                }
            contextMetadata.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        @Test
        fun `with a log level map`() {
            val defaultLevel = JLevel.FINE
            val mapping = "foo.bar" to defaultLevel
            val levelMap = LogLevelMap.create(mapOf(mapping), defaultLevel)
            val (logger, level) = mapping
            contextData.isLoggingForced(logger, level).shouldBeFalse()
            context.newContext()
                .withLogLevelMap(levelMap)
                .run {
                    contextData.isLoggingForced(logger, level).shouldBeTrue()
                    markCallbackExecuted()
                }
            contextData.isLoggingForced(logger, level).shouldBeFalse()
            checkCallbackWasExecuted()
        }

        @Test
        fun `with merged tags`() {
            val (name, value1, value2) = listOf("foo", "bar", "baz")
            val outerTags = Tags.of(name, value1)
            contextTags.shouldBeEmpty()
            context.newContext()
                .withTags(outerTags)
                .run {
                    contextTags shouldBe outerTags.asMap()
                    val innerTags = Tags.of(name, value2)
                    val allTags = outerTags.merge(innerTags)
                    context.newContext()
                        .withTags(innerTags)
                        .run {
                            // Double-check to be sure.
                            contextTags shouldBe allTags.asMap()
                            contextTags[name] shouldBe setOf(value1, value2)
                            markCallbackExecuted()
                        }
                    contextTags shouldBe outerTags.asMap()
                }
            contextTags.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        /**
         * Tests a context with concatenated metadata.
         *
         * Please note, metadata concatenation is different from metadata merging.
         * In particular, if during concatenation, several values are discovered
         * for a singleton key, then it effectively becomes repeated.
         *
         * And this aspect is checked by the test.
         */
        @Test
        @Suppress("MagicNumber") // The assertion is readable without a constant.
        fun `with concatenated metadata`() {
            val outerFoo = "outer-foo"
            val outerBar = "outer-bar"

            contextMetadata.shouldBeEmpty()
            context.newContext()
                .withMetadata(FOO, outerFoo)
                .withMetadata(BAR, outerBar)
                .run {
                    // `FOO` is a singleton key, so it should be unique.
                    // `BAR` is repeated, so it can't be asserted as unique.
                    contextMetadata shouldHaveSize 2
                    contextMetadata.shouldUniquelyContain(FOO, outerFoo)
                    contextMetadata.shouldContainInOrder(BAR, outerBar)

                    val innerFoo = "inner-foo"
                    val innerBar = "inner-bar"

                    context.newContext()
                        .withMetadata(FOO, innerFoo)
                        .withMetadata(BAR, innerBar)
                        .run {
                            // Note that singleton `FOO` is now asserted as a repeated key.
                            contextMetadata shouldHaveSize 4
                            contextMetadata.shouldContainInOrder(FOO, outerFoo, innerFoo)
                            contextMetadata.shouldContainInOrder(BAR, outerBar, innerBar)
                            markCallbackExecuted()
                        }

                    // Everything is restored after a scope.
                    contextMetadata shouldHaveSize 2
                    contextMetadata.shouldUniquelyContain(FOO, outerFoo)
                    contextMetadata.shouldContainInOrder(BAR, outerBar)
                }

            // Everything is restored after a scope.
            contextMetadata.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        @Test
        fun `with merged level maps`() {
            // Although, a logger name can be any `string`,
            // it is usually the name of a package or a class.
            val (other, fooBar, fooBarBaz) = listOf("other.package", "foo.bar", "foo.bar.Baz")

            // Everything in "foo.bar" gets at least FINE logging.
            val fooBarFine = levelMap(fooBar to JLevel.FINE)

            contextData.shouldNotForceLogging(JLevel.FINE, other, fooBar, fooBarBaz)
            context.newContext()
                .withLogLevelMap(fooBarFine)
                .run {
                    contextData.shouldForceLogging(JLevel.FINE, fooBar, fooBarBaz)
                    contextData.shouldNotForceLogging(JLevel.FINEST, fooBar, fooBarBaz)
                    contextData.shouldNotForceLogging(JLevel.FINE, other)

                    // Everything in "foo.bar.Baz" gets at least FINEST logging.
                    val fooBazFinest = levelMap(fooBarBaz to JLevel.FINEST)

                    context.newContext()
                        .withLogLevelMap(fooBazFinest)
                        .run {
                            contextData.shouldForceLogging(JLevel.FINE, fooBar, fooBarBaz)
                            contextData.shouldForceLogging(JLevel.FINEST, fooBarBaz)
                            contextData.shouldNotForceLogging(JLevel.FINE, other)
                            markCallbackExecuted()
                        }

                    // Everything is restored after a scope.
                    contextData.shouldForceLogging(JLevel.FINE, fooBar, fooBarBaz)
                    contextData.shouldNotForceLogging(JLevel.FINEST, fooBar, fooBarBaz)
                    contextData.shouldNotForceLogging(JLevel.FINE, other)
                }

            // Everything is restored after a scope.
            contextData.shouldNotForceLogging(JLevel.FINE, other, fooBar, fooBarBaz)
            checkCallbackWasExecuted()
        }

        @Test
        fun `with bound scope types`() {
            contextData.getScope(SUB_TASK).shouldBeNull()
            contextData.getScope(BATCH_JOB).shouldBeNull()

            context.newContext(SUB_TASK)
                .run {
                    val subTask = contextData.getScope(SUB_TASK)
                    subTask.shouldNotBeNull()
                    contextData.getScope(BATCH_JOB).shouldBeNull()

                    context.newContext(BATCH_JOB)
                        .run {
                            contextData.getScope(SUB_TASK) shouldBeSameInstanceAs subTask
                            contextData.getScope(BATCH_JOB).shouldNotBeNull()
                            markCallbackExecuted()
                        }

                    // Everything is restored after a scope.
                    contextData.getScope(SUB_TASK) shouldBeSameInstanceAs subTask
                    contextData.getScope(BATCH_JOB).shouldBeNull()
                }

            // Everything is restored after a scope.
            contextData.getScope(SUB_TASK).shouldBeNull()
            contextData.getScope(BATCH_JOB).shouldBeNull()

            checkCallbackWasExecuted()
        }
    }

    /**
     * As for now, general [Metadata] is not merged automatically
     * in the same way as [Tags].
     *
     * The code below only needs to be tested by one “real” implementation
     * to get coverage as tags merging is not done directly by data provider.
     *
     * Please note, it is needed to add tags manually inside a context to check
     * if there is a “real” context data provider installed. We can't use
     * [implementationUnderTest] here because these APIs go via the `Platform`
     * class, which uses the installed provider that may differ from what's
     * returned by [implementationUnderTest].
     */
    @Test
    fun `merge scope and log site tags`() {
        val backend = MemoizingLoggerBackend()
        val logger = ConfigurableLogger(backend)
        val logSiteTags = Tags.of("foo", "bar")
        val scopeTags = Tags.of("foo", "baz")

        ScopedLoggingContexts.newContext()
            .install()
            .use {
                val canAddTags = ScopedLoggingContexts.addTags(scopeTags)
                canAddTags shouldBe true
                logger.atInfo()
                    .with(Key.TAGS, logSiteTags)
                    .log("With tags")
            }

        // Merged tag values are ordered alphabetically.
        val expected = logSiteTags.merge(scopeTags)
        backend.logged shouldHaveSize 1
        backend.lastLogged.metadata.shouldUniquelyContain(Key.TAGS, expected)
    }

    @Test
    fun `not create a new scope instance if the same type is bound twice`() {
        contextData.getScope(SUB_TASK).shouldBeNull()
        context.newContext(SUB_TASK)
            .run {
                val taskScope = contextData.getScope(SUB_TASK)
                "$taskScope" shouldBe "sub task"

                context.newContext(SUB_TASK)
                    .run {
                        contextData.getScope(SUB_TASK) shouldBeSameInstanceAs taskScope
                        markCallbackExecuted()
                    }

                contextData.getScope(SUB_TASK) shouldBeSameInstanceAs taskScope
            }
        contextData.getScope(SUB_TASK).shouldBeNull()
        checkCallbackWasExecuted()
    }

    /**
     * This test verifies that a custom level set to a logger via a log level map can
     * be obtained via the [Platform].
     *
     * Please note that we use the top-level API of Spine Logging
     * e.g., [io.spine.logging.Level], instead of [java.util.logging.Level], or
     * [io.spine.logging.context.LogLevelMap], instead of
     * [io.spine.logging.jvm.context.LogLevelMap].
     * We do so because we want to test how the code works from the level of user's code
     * rather than implementation details.
     *
     * Since this class is an abstract base for tests of `ContextDataProvider` implementations,
     * the final test suite classes should provide testing for all the implementations from
     * the top-level API.
     */
    @Test
    fun `obtain custom log level set via a log level map`() {
        val loggerName = this::class.java.name
        val level = TLevel.DEBUG
        val map = TLogLevelMap.create(mapOf(loggerName to level))
        TScopedLoggingContext.newContext().withLogLevelMap(map).execute {
            val customLevel = Platform.getMappedLevel(loggerName)
            customLevel shouldBe level.toJavaLogging()
        }
    }
}

/**
 * Returns `true` if this [ContextDataProvider] forces logging for
 * the given [logger] at the specified [level].
 *
 * Otherwise, returns `false`.
 */
private fun ContextDataProvider.isLoggingForced(logger: LoggerName, level: JLevel): Boolean {
    // We expect that by default the specified level is disabled,
    // and the context itself would force the logging.
    val isEnabledByLevel = false
    val isForced = shouldForceLogging(logger, level, isEnabledByLevel)
    return isForced
}

private fun ContextDataProvider.shouldForceLogging(level: JLevel, vararg loggers: LoggerName) =
    loggers.forEach { logger ->
        isLoggingForced(logger, level).shouldBeTrue()
    }

private fun ContextDataProvider.shouldNotForceLogging(level: JLevel, vararg loggers: LoggerName) =
    loggers.forEach { logger ->
        isLoggingForced(logger, level).shouldBeFalse()
    }

private fun levelMap(
    mapping: Pair<LoggerName, JLevel>,
    defaultLevel: JLevel = JLevel.INFO
): LogLevelMap = LogLevelMap.create(mapOf(mapping), defaultLevel)
