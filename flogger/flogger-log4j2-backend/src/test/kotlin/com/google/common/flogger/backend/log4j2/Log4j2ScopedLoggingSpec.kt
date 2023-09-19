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

package com.google.common.flogger.backend.log4j2

import com.google.common.flogger.LogContext.Key
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.backend.log4j2.given.MemoizingAppender
import com.google.common.flogger.context.ContextDataProvider
import com.google.common.flogger.context.Tags
import com.google.common.flogger.testing.TestLogger
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for interaction between [ScopedLoggingContext][com.google.common.flogger.context.ScopedLoggingContext]
 * and [Log4j2LoggerBackend].
 *
 * [ScopedLoggingContext][com.google.common.flogger.context.ScopedLoggingContext]
 * is abstract. To test it with Log4j backend, a concrete implementation
 * is needed. This test suite uses [GrpcScopedLoggingContext][com.google.common.flogger.grpc.GrpcScopedLoggingContext].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/log4j2/src/test/java/com/google/common/flogger/backend/log4j2/Log4j2ScopedLoggingTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("With Log4j backend, `ScopedLoggingContext` should")
internal class Log4j2ScopedLoggingSpec {

    private val logger = createLogger()
    private val appender = MemoizingAppender()
    private var testLogger = createTestLogger(logger)
    private val logged = appender.events
    private val context = ContextDataProvider.getInstance().contextApiSingleton
    private val lastLogged get() = logged.last()

    @BeforeEach
    fun setUp() {
        logger.apply {
            level = Level.TRACE
            addAppender(appender)
        }
    }

    @AfterEach
    fun tearDown() {
        // Makes sure code within the scope has been executed.
        // Each test logs exactly once.
        logged.shouldHaveSize(1)
        logger.removeAppender(appender)
        appender.stop()
    }

    @Nested
    inner class
    `append a single tag` {

        private val singleTag = Tags.of("foo", "bar")
        private val expectedTags = stringify(singleTag)

        @Test
        fun `using the context`() {
            context.newContext()
                .withTags(singleTag)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `using a predefined key`() {
            context.newContext()
                .withMetadata(Key.TAGS, singleTag)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `using a custom key`() {
            val key = MetadataKey.single("tags", Tags::class.java)
            context.newContext()
                .withMetadata(key, singleTag)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }
    }

    @Nested
    inner class
    `append several tags` {

        private val severalTags = Tags.builder()
            .addTag("foo")
            .addTag("bar", "baz")
            .addTag("bar", "baz2")
            .build()
        private val expectedTags = stringify(severalTags)

        @Test
        fun `using the context`() {
            context.newContext()
                .withTags(severalTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `using a predefined key`() {
            context.newContext()
                .withMetadata(Key.TAGS, severalTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `using a custom key`() {
            val key = MetadataKey.single("tags", Tags::class.java)
            context.newContext()
                .withMetadata(key, severalTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }
    }

    @Nested
    inner class
    `ignore empty tags appended` {

        private val emptyTags = Tags.empty()
        private val expectedTags = null

        @Test
        fun `using the context`() {
            context.newContext()
                .withTags(emptyTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `using a predefined key`() {
            context.newContext()
                .withMetadata(Key.TAGS, emptyTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `using a custom key`() {
            val key = MetadataKey.single("tags", Tags::class.java)
            context.newContext()
                .withMetadata(key, emptyTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }
    }

    @Nested
    inner class
    `merge tags appended using the context` {

        private val contextTags = Tags.builder()
            .addTag("foo")
            .addTag("bar", "baz")
            .addTag("bar", "baz2")
            .build()
        private val expectedContextTags = stringify(contextTags).substring(1)
        private val singletonKey = MetadataKey.single("tags", String::class.java)
        private val singletonListKey = MetadataKey.single("tags", List::class.java)
        private val repeatedKey = MetadataKey.repeated("tags", String::class.java)

        @Test
        fun `and a singleton metadata key`() {
            val metadataTag = "sTag1"
            val expectedTags = "[$metadataTag, $expectedContextTags"
            context.newContext()
                .withMetadata(singletonKey, metadataTag)
                .withTags(contextTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `and several singleton metadata keys`() {
            val metadataTags = listOf("sTag1", "sTag2", "sTag3")
            val expectedTags = "[${metadataTags[2]}, $expectedContextTags"
            context.newContext()
                .withMetadata(singletonKey, metadataTags[0])
                .withMetadata(singletonKey, metadataTags[1])
                .withMetadata(singletonKey, metadataTags[2]) // Only the last one will be kept.
                .withTags(contextTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `and a singleton metadata key for lists`() {
            val metadataTag = listOf(1, 2, 3) // It is a SINGLE tag.
            val expectedTags = "[$metadataTag, $expectedContextTags"
            context.newContext()
                .withMetadata(singletonListKey, metadataTag)
                .withTags(contextTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `and a repeated metadata key`() {
            val metadataTag = "rTag1"
            val expectedTags = "[$metadataTag, $expectedContextTags"
            context.newContext()
                .withMetadata(repeatedKey, metadataTag)
                .withTags(contextTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `and several repeated metadata keys`() {
            val metadataTags = listOf("rTag1", "rTag2", "rTag3")
            val expectedMetadataTags = metadataTags.joinToString(", ")
            val expectedTags = "[$expectedMetadataTags, $expectedContextTags"
            context.newContext()
                .withMetadata(repeatedKey, metadataTags[0])
                .withMetadata(repeatedKey, metadataTags[1])
                .withMetadata(repeatedKey, metadataTags[2]) // All should be kept.
                .withTags(contextTags)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextTags shouldBe expectedTags
                }
        }

        @Test
        fun `, several repeated metadata keys and a singleton key for list`() {
            val metadataListTag = listOf(1, 2, 3) // It is a SINGLE tag.
            val metadataTags = listOf("rsTag1", "rsTag2", "rsTag3")
            val expectedMetadataTags = metadataTags.joinToString(", ")
            val expectedTags = "[$metadataListTag, $expectedMetadataTags, $expectedContextTags"
            context.newContext()
                .withMetadata(singletonListKey, metadataListTag)
                .withMetadata(repeatedKey, metadataTags[0])
                .withMetadata(repeatedKey, metadataTags[1])
                .withMetadata(repeatedKey, metadataTags[2])
                .withTags(contextTags)
                .install()
                .use {
                    testLogger.atInfo().log("test")
                    lastLogged.contextTags shouldBe expectedTags
                }
        }
    }

    @Nested
    inner class
    `append metadata entries` {

        private val singletonKey = MetadataKey.single("sKey", Int::class.javaObjectType)

        @Test
        fun `using a singleton metadata key`() {
            val (key, value) = singletonKey to 23
            val expectedContext = mapOf(key.label to "$value")
            context.newContext()
                .withMetadata(key, value)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextDataMap shouldBe expectedContext
                }
        }

        @Test
        fun `using the same singleton key several times`() {
            val (key, values) = singletonKey to listOf(1, 4, 7)
            val expectedContext = mapOf(key.label to "${values.last()}")
            context.newContext()
                .withMetadata(singletonKey, values[0])
                .withMetadata(singletonKey, values[1])
                .withMetadata(singletonKey, values[2]) // Only the last one should be kept.
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextDataMap shouldBe expectedContext
                }
        }

        @Test
        fun `using different singleton keys with the same label`() {
            val label = "id"
            val (key1, value1) = MetadataKey.single(label, String::class.java) to "001"
            val (key2, value2) = MetadataKey.single(label, String::class.java) to "002"
            val expectedContext = mapOf(label to "${listOf(value1, value2)}")
            context.newContext()
                .withMetadata(key1, value1)
                .withMetadata(key2, value2) // In this case, values are concatenated.
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextDataMap shouldBe expectedContext
                }
        }

        @Test
        fun `using a singleton key for lists`() {
            val listKey = MetadataKey.single("items", List::class.java)
            val items = listOf(23, 33, 56)
            val expectedContext = mapOf(listKey.label to "$items")
            context.newContext()
                .withMetadata(listKey, items)
                .install()
                .use {
                    testLogger.atInfo().log()
                    lastLogged.contextDataMap shouldBe expectedContext
                }
        }
    }

    @Test
    fun `append arbitrary metadata and tags`() {
        val (key, value) = MetadataKey.single("count", Int::class.javaObjectType) to 23
        val tags = Tags.builder()
            .addTag("foo")
            .addTag("baz", "bar")
            .addTag("baz", "bar2")
            .build()
        val expectedContext = mapOf(
            "tags" to stringify(tags),
            key.label to "$value"
        )
        context.newContext()
            .withMetadata(key, value)
            .withTags(tags)
            .install()
            .use {
                testLogger.atInfo().log()
                lastLogged.contextDataMap shouldBe expectedContext
            }
    }

    @Test
    fun `use metadata from an outer context`() {
        val key = MetadataKey.single("id", String::class.java)
        val outerValue = "outerID"
        val outerTags = Tags.builder()
            .addTag("foo")
            .addTag("baz", "bar")
            .build()

        context.newContext()
            .withMetadata(key, outerValue)
            .withTags(outerTags)
            .install()
            .use {

                val innerValue = "innerID"
                val innerTags = Tags.builder()
                    .addTag("foo")
                    .addTag("baz", "bar2")
                    .build()

                val expectedInnerContext = mapOf(
                    "tags" to stringify(outerTags.merge(innerTags)),
                    key.label to innerValue // It is a singleton key. It uses the last value.
                )

                context.newContext()
                    .withMetadata(key, innerValue)
                    .withTags(innerTags)
                    .install()
                    .use {
                        testLogger.atInfo().log()
                        lastLogged.contextDataMap shouldBe expectedInnerContext
                    }
            }
    }
}

private val serialNumbers = AtomicInteger()

/**
 * Creates a logger with a unique name.
 *
 * A unique name should produce a different logger for each test,
 * allowing tests to be run in parallel.
 */
private fun createLogger(): Logger {
    val suiteName = Log4j2ScopedLoggingSpec::class.simpleName!!
    val testSerial = serialNumbers.incrementAndGet()
    val loggerName = "%s_%02d".format(suiteName, testSerial)
    val logger = LogManager.getLogger(loggerName) as Logger
    return logger
}

/**
 * Creates a new [TestLogger] with the given [logger] to be used
 * as a delegate.
 */
private fun createTestLogger(logger: Logger): TestLogger {
    val backend = Log4j2LoggerBackend(logger)
    val testLogger = TestLogger.create(backend)
    return testLogger
}

/**
 * Converts the given [tags] to string in a way similar to
 * values from Log4j's `ReadOnlyStringMap`.
 */
private fun stringify(tags: Tags): String {
    val entries = tags.asMap().map { (name, values) ->
        if (values.size == 0) {
            name
        } else {
            values.joinToString(", ") { value ->
                "$name=$value"
            }
        }
    }
    val result = "[${entries.joinToString(", ")}]"
    return result
}

private val LogEvent.contextTags
    get() = contextData.toMap()["tags"]

private val LogEvent.contextDataMap
    get() = contextData.toMap()