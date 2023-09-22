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

package io.spine.logging.backend.system

import io.spine.logging.backend.system.given.FakeBackendFactory
import io.spine.logging.backend.system.given.FixedTime
import io.spine.logging.backend.system.given.NoOpCallerFinder
import io.spine.logging.backend.system.given.StubClockService
import io.spine.logging.backend.system.given.StubContextDataProviderService
import io.spine.logging.backend.system.given.StubBackendFactoryService
import com.google.common.flogger.context.ContextDataProvider
import com.google.common.flogger.testing.FakeLoggerBackend
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests [DefaultPlatform].
 *
 * This test suite checks that the internal implementation of the configured
 * platform “plugins” works as expected, but it doesn't really test
 * the singleton behavior, since the precise platform loaded at runtime
 * can vary in details.
 *
 * @see <a href="https://github.com/google/flogger/blob/70c5aea863952ee61b3d33afb41f2841b6d63455/api/src/test/java/com/google/common/flogger/backend/system/DefaultPlatformTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`DefaultPlatform` should")
internal class DefaultPlatformSpec {

    private val factory = FakeBackendFactory()
    private val context = ContextDataProvider.getNoOpProvider()
    private val clock = FixedTime()
    private val caller = NoOpCallerFinder()
    private var platform = object : DefaultPlatform(factory, context, clock, caller) { }

    @Test
    fun `use the given factory to create backend instances`() {
        val loggerName = "logger.name"
        val backend = platform.getBackendImpl(loggerName)
        backend.loggerName shouldContain loggerName
        backend::class shouldBe FakeLoggerBackend::class
    }

    @Test
    fun `return the configured context provider`() {
        val contextProvider = platform.contextDataProviderImpl
        contextProvider shouldBeSameInstanceAs context
    }

    @Test
    fun `use the given clock to provide the current time`() {
        val randomTimestamp = Math.random().toLong()
        clock.returnedTimestamp = randomTimestamp
        val timestamp = platform.currentTimeNanosImpl
        timestamp shouldBe randomTimestamp
    }

    @Test
    fun `return the configured caller finder`() {
        val callerFinder = platform.callerFinderImpl
        callerFinder shouldBeSameInstanceAs caller
    }

    @Test
    fun `return a human-readable string describing the platform configuration`() {
        val configInfo = platform.configInfoImpl.trimEnd()
        val expectedConfig = """
            Platform: ${platform.javaClass.name}
            BackendFactory: $factory
            Clock: $clock
            ContextDataProvider: $context
            LogCallerFinder: $caller
        """.trimIndent()
        configInfo shouldBe expectedConfig
    }

    @Test
    fun `load services from the classpath`() {
        val platform = DefaultPlatform()
        val configInfo = platform.configInfoImpl.trimEnd()
        val expectedServices = setOf(
            "BackendFactory: ${StubBackendFactoryService::class.simpleName}",
            "Clock: ${StubClockService::class.simpleName}",
            "ContextDataProvider: ${StubContextDataProviderService::class.simpleName}"
        )
        expectedServices.forEach { service ->
            configInfo shouldContain service
        }
    }
}
