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

import io.spine.logging.jvm.LoggingScope
import io.spine.logging.jvm.LoggingScopeProvider
import io.spine.logging.jvm.util.Checks.checkNotNull
import org.jspecify.annotations.Nullable

/**
 * Singleton keys which identify different types of scopes which scoped contexts can be bound to.
 *
 * To bind a context to a scope type, create the context with that type:
 *
 * ```
 * ScopedLoggingContext.getInstance().newScope(REQUEST).run { someTask(x, y, z) }
 * ```
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ScopeType.java)
 * for historical context.
 */
public class ScopeType private constructor(private val name: String) : LoggingScopeProvider {

    /**
     * Called by ScopedLoggingContext to make a new scope instance when a context is installed.
     */
    internal fun newScope(): LoggingScope {
        return LoggingScope.create(name)
    }

    override fun getCurrentScope(): LoggingScope? {
        return ContextDataProvider.getInstance().getScope(this)
    }

    public companion object {
        /**
         * The built-in "request" scope. This can be bound to a scoped context in order to provide a
         * distinct request scope for each context, allowing stateful logging operations (e.g., rate
         * limiting) to be scoped to the current request.
         *
         * Enable a request scope using:
         *
         * ```
         * ScopedLoggingContext.getInstance().newScope(REQUEST).run { scopedMethod(x, y, z) }
         * ```
         *
         * which runs `scopedMethod` with a new "request" scope for the duration of the context.
         *
         * Then use per-request rate limiting using:
         *
         * ```
         * logger.atWarning().atMostEvery(5, SECONDS).per(REQUEST).log("Some error message...")
         * ```
         *
         * Note that in order for the request scope to be applied to a log statement, the
         * `per(REQUEST)` method must still be called; just being inside the request scope isn't enough.
         */
        @JvmField
        public val REQUEST: ScopeType = create("request")

        /**
         * Creates a new Scope type, which can be used as a singleton key to identify a scope during
         * scoped context creation or logging. Callers are expected to retain this key in a static field
         * or return it via a static method. Scope types have singleton semantics and two scope types with
         * the same name are *NOT* equivalent.
         *
         * @param name a debug friendly scope identifier (e.g. "my_batch_job").
         */
        @JvmStatic
        public fun create(name: String): ScopeType {
            return ScopeType(checkNotNull(name, "name"))
        }
    }
}
