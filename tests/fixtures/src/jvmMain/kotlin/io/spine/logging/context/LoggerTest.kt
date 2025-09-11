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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldNotContain
import io.spine.logging.Level
import io.spine.logging.Logger
import io.spine.logging.LoggingFactory
import io.spine.logging.testing.tapConsole
import kotlin.reflect.KClass

/**
 * Abstract base for testing [Logger] implementations in test suites covering
 * combinations of backends and contexts.
 *
 * @property cls The class for which the [logger] is created.
 *   Must have the [qualified name][KClass.qualifiedName].
 */
public abstract class LoggerTest(
    protected val cls: KClass<*>,
    body: ShouldSpec.() -> Unit = {}
) : ShouldSpec(body) {

    /**
     * The logger under the test, which is created for the [given class][cls].
     */
    protected val logger: Logger<*> = LoggingFactory.forEnclosingClass()

    init {
        @Suppress("LeakingThis")
        should("not log if the `Level.OFF` is set via a map") {
            val map = mapOf(cls.qualifiedName!! to Level.OFF)
            val levelMap = LogLevelMap.create(map)
            val logMessage = "This should not be logged."
            var consoleOutput = ""
            ScopedLoggingContext.getInstance().newContext().withLogLevelMap(levelMap).run {
                consoleOutput = tapConsole {
                    logger.atError().log {
                        logMessage
                    }
                }
            }
            consoleOutput shouldNotContain logMessage
        }
    }
}
