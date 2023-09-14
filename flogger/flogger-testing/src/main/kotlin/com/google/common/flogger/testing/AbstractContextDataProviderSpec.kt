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

package com.google.common.flogger.testing

import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.backend.Metadata
import com.google.common.flogger.context.ContextDataProvider
import com.google.common.flogger.context.LogLevelMap
import com.google.common.flogger.context.ScopeType
import com.google.common.flogger.context.ScopedLoggingContext
import com.google.common.flogger.context.ScopedLoggingContexts
import com.google.common.flogger.context.Tags
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.logging.Level
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Set of common tests for [ContextDataProvider]s.
 *
 * @see <a href="https://github.com/google/flogger/blob/70c5aea863952ee61b3d33afb41f2841b6d63455/api/src/test/java/com/google/common/flogger/testing/AbstractScopedLoggingContextTest.java">
 *     Original Java code of Google Flogger</a>
 */
@Suppress("FunctionName", "ClassName") // Tests aren't recognized in `main` sources.
@DisplayName("`ContextDataProvider` should") // This name is to be overridden by inheritors.
public abstract class AbstractContextDataProviderSpec {

    private lateinit var dataProvider: ContextDataProvider
    private lateinit var context: ScopedLoggingContext

    private val providerTags: Map<String?, Set<Any?>?>
        get() = dataProvider.tags.asMap()
    private val providerMetadata: Metadata
        get() = dataProvider.metadata

    /**
     * A flag to be set inside an innermost callback to prove it was executed.
     */
    private var callbackWasExecuted = false

    /**
     * Returns the tested [ContextDataProvider].
     */
    protected abstract val implementationUnderTest: ContextDataProvider

    private companion object {
        private val FOO = MetadataKey.single("foo", String::class.java)
        private val BAR = MetadataKey.repeated("bar", String::class.java)
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
    public fun setImplementation() {
        dataProvider = implementationUnderTest
        context = dataProvider.getContextApiSingleton()
    }

    /**
     * Checks that an innermost callback within a test was executed.
     *
     * To make this method work, call [markCallbackExecuted] at the end
     * of the innermost callback.
     *
     * Don't use `@AfterEach` here because the subclass may not use this in
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

    @Nested
    public inner class
    `create a new context` {

        @Test
        public fun `with tags`() {
            val tags = Tags.of("foo", "bar")
            providerTags.shouldBeEmpty()
            context.newContext()
                .withTags(tags)
                .run {
                    providerTags shouldBe tags.asMap()
                    markCallbackExecuted()
                }
            providerTags.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        @Test
        public fun `with metadata`() {
            val (key, value) = FOO to "foo"
            providerMetadata.shouldBeEmpty()
            context.newContext()
                .withMetadata(key, value)
                .run {
                    // Should be unique because the key is singleton.
                    providerMetadata.shouldUniquelyContain(key, value)
                    markCallbackExecuted()
                }
            providerMetadata.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        @Test
        public fun `with a log level map`() {
            val defaultLevel = Level.FINE
            val mapping = "foo.bar" to defaultLevel
            val levelMap = LogLevelMap.create(mapOf(mapping), defaultLevel)
            val (logger, level) = mapping
            dataProvider.isLoggingForced(logger, level).shouldBeFalse()
            context.newContext()
                .withLogLevelMap(levelMap)
                .run {
                    dataProvider.isLoggingForced(logger, level).shouldBeTrue()
                    markCallbackExecuted()
                }
            dataProvider.isLoggingForced(logger, level).shouldBeFalse()
            checkCallbackWasExecuted()
        }

        @Test
        public fun `with merged tags`() {
            val (name, value1, value2) = listOf("foo", "bar", "baz")
            val outerTags = Tags.of(name, value1)
            providerTags.shouldBeEmpty()
            context.newContext()
                .withTags(outerTags)
                .run {
                    providerTags shouldBe outerTags.asMap()
                    val innerTags = Tags.of(name, value2)
                    val allTags = outerTags.merge(innerTags)
                    context.newContext()
                        .withTags(innerTags)
                        .run {
                            // Double-check to be sure.
                            providerTags shouldBe allTags.asMap()
                            providerTags[name] shouldBe setOf(value1, value2)
                            markCallbackExecuted()
                        }
                    providerTags shouldBe outerTags.asMap()
                }
            providerTags.shouldBeEmpty()
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
        public fun `with concatenated metadata`() {
            val outerFoo = "outer-foo"
            val outerBar = "outer-bar"

            providerMetadata.shouldBeEmpty()
            context.newContext()
                .withMetadata(FOO, outerFoo)
                .withMetadata(BAR, outerBar)
                .run {
                    // `FOO` is a singleton key, so it should be unique.
                    // `BAR` is repeated, so it can't be asserted as unique.
                    providerMetadata shouldHaveSize 2
                    providerMetadata.shouldUniquelyContain(FOO, outerFoo)
                    providerMetadata.shouldContainInOrder(BAR, outerBar)

                    val innerFoo = "inner-foo"
                    val innerBar = "inner-bar"

                    context.newContext()
                        .withMetadata(FOO, innerFoo)
                        .withMetadata(BAR, innerBar)
                        .run {
                            // Note that singleton `FOO` is now asserted as a repeated key.
                            providerMetadata shouldHaveSize 4
                            providerMetadata.shouldContainInOrder(FOO, outerFoo, innerFoo)
                            providerMetadata.shouldContainInOrder(BAR, outerBar, innerBar)
                            markCallbackExecuted()
                        }

                    // Everything is restored after a scope.
                    providerMetadata shouldHaveSize 2
                    providerMetadata.shouldUniquelyContain(FOO, outerFoo)
                    providerMetadata.shouldContainInOrder(BAR, outerBar)
                }

            // Everything is restored after a scope.
            providerMetadata.shouldBeEmpty()
            checkCallbackWasExecuted()
        }

        @Test
        public fun `with merged level maps`() {
            // Although, a logger name can be any `string`,
            // it is usually the name of a package or a class.
            val all = listOf("other.package", "foo.bar", "foo.bar.Baz")
            val (other, fooBar, fooBarBaz) = all

            all.forEach { pkg ->
                dataProvider.isLoggingForced(pkg, Level.FINE).shouldBeFalse()
            }

            // Everything in "foo.bar" gets at least FINE logging.
            val fooBarFine = LogLevelMap.create(
                mapOf(fooBar to Level.FINE),
                Level.INFO
            )

            context.newContext()
                .withLogLevelMap(fooBarFine)
                .run {
                    dataProvider.isLoggingForced(other, Level.FINE).shouldBeFalse()
                    dataProvider.isLoggingForced(fooBar, Level.FINE).shouldBeTrue()
                    dataProvider.isLoggingForced(fooBarBaz, Level.FINE).shouldBeTrue()
                    dataProvider.isLoggingForced(fooBarBaz, Level.FINEST).shouldBeFalse()

                    // Everything in "foo.bar.Baz" gets at least FINEST logging.
                    val fooBazFinest = LogLevelMap.create(
                        mapOf(fooBarBaz to Level.FINEST),
                        Level.INFO
                    )

                    context.newContext()
                        .withLogLevelMap(fooBazFinest)
                        .run {
                            dataProvider.isLoggingForced(other, Level.FINE).shouldBeFalse()
                            dataProvider.isLoggingForced(fooBar, Level.FINE).shouldBeTrue()
                            dataProvider.isLoggingForced(fooBarBaz, Level.FINEST).shouldBeTrue()
                            markCallbackExecuted()
                        }

                    // Everything is restored after a scope.
                    dataProvider.isLoggingForced(other, Level.FINE).shouldBeFalse()
                    dataProvider.isLoggingForced(fooBar, Level.FINE).shouldBeTrue()
                    dataProvider.isLoggingForced(fooBarBaz, Level.FINE).shouldBeTrue()
                    dataProvider.isLoggingForced(fooBarBaz, Level.FINEST).shouldBeFalse()
                }

            // Everything is restored after a scope.
            all.forEach { pkg ->
                dataProvider.isLoggingForced(pkg, Level.FINE).shouldBeFalse()
            }
            checkCallbackWasExecuted()
        }

        @Test
        public fun `with bound scope types`() {
            dataProvider.getScope(SUB_TASK).shouldBeNull()
            dataProvider.getScope(BATCH_JOB).shouldBeNull()

            context.newContext(SUB_TASK)
                .run {
                    val taskScope = dataProvider.getScope(SUB_TASK)
                    taskScope.shouldNotBeNull()
                    dataProvider.getScope(BATCH_JOB).shouldBeNull()

                    context.newContext(BATCH_JOB)
                        .run {
                            dataProvider.getScope(SUB_TASK) shouldBeSameInstanceAs taskScope
                            dataProvider.getScope(BATCH_JOB).shouldNotBeNull()
                            markCallbackExecuted()
                        }

                    // Everything is restored after a scope.
                    dataProvider.getScope(SUB_TASK) shouldBeSameInstanceAs taskScope
                    dataProvider.getScope(BATCH_JOB).shouldBeNull()
                }

            // Everything is restored after a scope.
            dataProvider.getScope(SUB_TASK).shouldBeNull()
            dataProvider.getScope(BATCH_JOB).shouldBeNull()

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
    public fun `merge scope and log site tags`() {
        val backend = FakeLoggerBackend()
        val logger = TestLogger.create(backend)
        val logSiteTags = Tags.of("foo", "bar")
        val scopeTags = Tags.of("foo", "baz")

        var canAddTags: Boolean
        ScopedLoggingContexts.newContext()
            .install()
            .use {
                canAddTags = ScopedLoggingContexts.addTags(scopeTags)
                logger.atInfo()
                    .with(Key.TAGS, logSiteTags)
                    .log("With tags")
            }

        // Merged tag values are ordered alphabetically.
        val merged = logSiteTags.merge(scopeTags)
        val expected = if (canAddTags) merged else logSiteTags
        backend.logged shouldHaveSize 1
        backend.lastLogged.metadata.shouldUniquelyContain(Key.TAGS, expected)
    }

    @Test
    public fun `not create a new scope instance if the same type is bound twice`() {
        dataProvider.getScope(SUB_TASK).shouldBeNull()
        context.newContext(SUB_TASK)
            .run {
                val taskScope = dataProvider.getScope(SUB_TASK)
                "$taskScope" shouldBe "sub task"

                context.newContext(SUB_TASK)
                    .run {
                        dataProvider.getScope(SUB_TASK) shouldBeSameInstanceAs taskScope
                        markCallbackExecuted()
                    }

                dataProvider.getScope(SUB_TASK) shouldBeSameInstanceAs taskScope
            }
        dataProvider.getScope(SUB_TASK).shouldBeNull()
        checkCallbackWasExecuted()
    }
}

/**
 * Says whether the context forces logging from the given logger
 * at the specified level.
 */
private fun ContextDataProvider.isLoggingForced(loggerName: String, level: Level): Boolean {
    // We expect that by default the specified level is disabled,
    // and the context itself would force the logging.
    val isEnabledByLevel = false
    val isForced = shouldForceLogging(loggerName, level, isEnabledByLevel)
    return isForced
}

