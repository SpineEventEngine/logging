/*
 * Copyright 2024, TeamDev. All rights reserved.
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

import io.spine.logging.jvm.backend.Platform
import io.kotest.matchers.shouldBe
import io.spine.logging.Level
import io.spine.logging.backend.log4j2.Log4j2BackendFactory
import io.spine.logging.context.BaseLogLevelMapTest
import io.spine.logging.context.std.StdContextDataProvider
import io.spine.logging.testing.Log4j2Recorder
import io.spine.logging.testing.Recorder
import org.junit.jupiter.api.Test

/**
 * This is a non-abstract integration test of [LogLevelMap][io.spine.logging.context.LogLevelMap]
 * executed in the project in which logging backend is based on Log4j2.
 *
 * Please see `build.gradle.kts` of this module for the details.
 */
internal class LogLevelMapITest: BaseLogLevelMapTest() {

    override fun createRecorder(loggerName: String, minLevel: Level): Recorder =
        Log4j2Recorder(loggerName, minLevel)

    @Test
    fun `should use 'Log4j2LoggerBackend`() {
        val loggerName = this::class.qualifiedName!!
        val platformProvided = Platform.getBackend(loggerName)
        val factoryProvided = Log4j2BackendFactory().create(loggerName)
        platformProvided::class shouldBe factoryProvided::class
    }

    @Test
    fun `should use 'StdContextDataProvider'`() {
        val provider = Platform.getContextDataProvider()
        provider::class shouldBe StdContextDataProvider::class
    }
}
