/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.backend.system

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.AbstractLogger
import io.spine.logging.LogSite
import io.spine.logging.backend.LogCallerFinder
import io.spine.logging.injectedLogSite
import io.spine.reflect.CallerFinder
import kotlin.reflect.KClass

/**
 * The default caller finder implementation for Java 9+.
 *
 * See class documentation in [LogCallerFinder] for important implementation restrictions.
 */
@Immutable
@ThreadSafe
public class StackBasedCallerFinder private constructor() : LogCallerFinder() {

    public override fun findLoggingClass(loggerClass: KClass<out AbstractLogger<*>>): String {
        val javaClass = loggerClass.java
        // We can skip at most only 1 method from the analysis, the inferLoggingClass() method itself.
        val caller = CallerFinder.findCallerOf(javaClass, 1)
        if (caller != null) {
            return caller.className
        }
        error("No caller found on the stack for: ${javaClass.name}")
    }

    public override fun findLogSite(loggerApi: KClass<*>, stackFramesToSkip: Int): LogSite {
        val javaClass = loggerApi.java
        // Skip an additional frame due to Throwable instantiation in this method.
        val caller = CallerFinder.findCallerOf(javaClass, stackFramesToSkip + 1)
        return if (caller == null) {
            LogSite.Invalid
        } else {
            injectedLogSite(
                caller.className.replace('.', '/'),
                caller.methodName,
                caller.lineNumber,
                caller.fileName
            )
        }
    }

    override fun toString(): String = "Default stack-based caller finder"

    public companion object {
        private val INSTANCE: LogCallerFinder = StackBasedCallerFinder()
        /** Called during logging platform initialization; MUST NOT call any code that might log. */
        @JvmStatic
        public fun getInstance(): LogCallerFinder = INSTANCE
    }
}
