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

package io.spine.logging.context

import given.map.LoggingTestFixture
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.spine.logging.Level
import io.spine.logging.testing.Recorder
import io.spine.logging.testing.checkLogging
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * Base class for integration test suites of [LogLevelMap].
 *
 * This class is responsible for the general-purpose configuration of a test suite.
 * For the actual definition of tests, please see [BaseLogLevelMapTest].
 *
 * @see BaseLogLevelMapTest
 */
public abstract class AbstractLogLevelMapTest(
    body: ShouldSpec.() -> Unit = {}
) : ShouldSpec(body) {

    private var closable: AutoCloseable? = null

    /**
     * Creates and populates the builder of a scoped logging context
     * delegating the configuration of the builder to [configureBuilder] method.
     */
    private fun createContext(): ScopedLoggingContext.Builder {
        val map = LogLevelMap.builder().let {
            configureBuilder(it)
            it.build()
        }
        val context = ScopedLoggingContext.getInstance()
            .newContext()
            .withLogLevelMap(map)
        return context
    }

    protected abstract fun configureBuilder(builder: LogLevelMap.Builder)

    /**
     * Installs logging context with the configured log level map.
     *
     * We do it via [beforeEach] rather than [beforeSpec] to make sure that
     * a test is executed in the same thread.
     *
     * @see [afterEach]
     */
    override suspend fun beforeEach(testCase: TestCase) {
        super.beforeEach(testCase)
        val context = createContext()
        closable = context.install()
    }

    /**
     * Removes the currently installed logging context.
     *
     * @see [beforeEach]
     */
    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        closable?.close()
        super.afterEach(testCase, result)
    }

    /**
     * Extending classes should create a [Recorder] specific to the currently
     * used logging backend.
     */
    protected abstract fun createRecorder(loggerName: String, minLevel: Level): Recorder

    /**
     * Executes the [test] with the started logging [Recorder].
     *
     * @param T The type of the logging test fixture to be used in the test.
     * @param displayName The name of the test.
     * @param loggingClass The class of the logging test fixture used in the test.
     * @param test The block with the testing code.
     */
    protected fun <T: LoggingTestFixture> should(
        displayName: String,
        loggingClass: KClass<T>,
        test: Recorder.(fixture: LoggingTestFixture) -> Unit
    ) {
        val recorder = createRecorder(loggingClass.qualifiedName!!, Level.ALL)
        should(displayName) {
            checkLogging(recorder) {
                val fixture = loggingClass.createInstance()
                test(fixture)
            }
        }
    }

    /**
     * Executes the [test] with fixtures created for the given [loggingClasses].
     *
     * @param T The type of the logging test fixtures to be used in the test.
     * @param displayName The name of the test.
     * @param loggingClasses The classes of the logging test fixture used in the test.
     * @param test The block with the testing code.
     */
    protected fun <T : LoggingTestFixture> shouldMany(
        displayName: String,
        loggingClasses: List<KClass<out T>>,
        test: (fixtures: List<T>, recorders: List<Recorder>) -> Unit
    ) {
        val recorders = loggingClasses.map { cls ->
            createRecorder(cls.qualifiedName!!, Level.ALL)
        }
        should(displayName) {
            checkLogging(recorders) {
                val fixtures = loggingClasses.map { it.createInstance() }
                test(fixtures, recorders)
            }
        }
    }
}
