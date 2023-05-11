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

package io.spine.logging.context

import given.map.L1Direct
import given.map.LoggingTestFixture
import given.map.nested.L2Direct
import io.kotest.core.spec.style.ExpectSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.logging.Level
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.WARNING
import io.spine.testing.logging.Recorder
import io.spine.testing.logging.checkLogging
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

abstract class AbstractLogLevelMapSpec: ExpectSpec() {

    private val map = lazy {
        val builder = LogLevelMap.builder()
        configureBuilder(builder)
        builder.build()
    }

    private val context by lazy {
        ScopedLoggingContext.newContext()
            .withLogLevelMap(map.value)
    }

    private var closable: AutoCloseable? = null

    open fun configureBuilder(builder: LogLevelMap.Builder) {
        with(builder) {
            setDefault(defaultLevel)
            add(level1, L1Direct::class)
            add(level2Direct, L2Direct::class)
        }
    }

    companion object {
        val defaultLevel = ERROR
        val level1 = DEBUG
        val level2Direct = WARNING
        val level2Package = INFO
    }

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

    abstract fun createRecorder(loggerName: String, minLevel: Level): Recorder

    private fun test(
        displayName: String,
        loggingClass: KClass<*>,
        test: Recorder.(fixture: LoggingTestFixture) -> Unit
    ) {
        val recorder = createRecorder(loggingClass.qualifiedName!!, Level.ALL)
        expect(displayName) {
            checkLogging(recorder) {
                val fixture = loggingClass.createInstance() as LoggingTestFixture
                test(fixture)
            }
        }
    }

    init {
        test("default level is set", DefLoggingFixture::class) {
            it.atError()
            records shouldHaveSize 1
            val record = records[0]
            record.level shouldBe ERROR
        }

        test("direct level for a class prevails", L1Direct::class) {
            it.atDebug()
            records shouldHaveSize 1
            val record = records[0]
            record.level shouldBe DEBUG
        }
    }
}

/**
 * The fixture which is not mentioned in log level map, and package of which
 * is outside the package hierarchy configured in the map.
 */
class DefLoggingFixture: LoggingTestFixture()
