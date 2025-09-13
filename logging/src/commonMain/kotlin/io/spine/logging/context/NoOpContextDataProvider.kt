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

package io.spine.logging.context

import io.spine.logging.MetadataKey
import io.spine.logging.StackSize
import io.spine.logging.WithLogging
import kotlinx.atomicfu.atomic

/**
 * Fallback context data provider used when no other implementations are available for a platform.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/NoOpContextDataProvider.java">
 *   Original Java code</a> for historical context.
 */
internal class NoOpContextDataProvider private constructor() : ContextDataProvider() {

    private val noOpContext = NoOpScopedLoggingContext()

    override fun getContextApiSingleton(): ScopedLoggingContext = noOpContext
    override fun toString(): String = "No-op Provider"

    companion object {

        /**
         * Returns a singleton "no op" instance of the context data provider
         * API which logs a warning if used in code which attempts to set context
         * information or modify scopes.
         *
         * This is intended for use by platform implementations in cases
         * where no context is configured.
         */
        @get:JvmName("getNoOpInstance")
        @JvmStatic
        val noOpInstance by lazy {
            NoOpContextDataProvider()
        }
    }
}

private class NoOpScopedLoggingContext : ScopedLoggingContext(), AutoCloseable {

    // Since the ContextDataProvider class is loaded during Platform initialization we must be very
    // careful to avoid any attempt to obtain a logger instance until we can be sure logging config
    // is complete.
    private object LazyLogger : WithLogging

    private val haveWarned = atomic(false)

    private fun logWarningOnceOnly() {
        if (haveWarned.compareAndSet(expect = false, update = true)) {
            val defaultPlatform = "io.spine.logging.backend.system.DefaultPlatform"
            LazyLogger.logger
                .atWarning()
                .withStackTrace(StackSize.SMALL)
                .log {
                    """
                    Scoped logging contexts are disabled; no context data provider was installed.
                    To enable scoped logging contexts in your application, see the site-specific
                    `Platform` class used to configure logging behaviour.
                    Default `Platform`: `$defaultPlatform`.
                    """.trimIndent()
                }
        }
    }

    override fun newContext(): Builder {

        return object : Builder() {
            override fun install(): AutoCloseable {
                logWarningOnceOnly()
                return this@NoOpScopedLoggingContext
            }
        }
    }

    override fun newContext(scopeType: ScopeType?): Builder =
        // Ignore scope bindings when there's no way to propagate them.
        newContext()

    override fun addTags(tags: Tags): Boolean {
        logWarningOnceOnly()
        // Superclass methods still do argument checking, which is important for consistent behaviour.
        return super.addTags(tags)
    }

    override fun <T : Any> addMetadata(key: MetadataKey<T>, value: T): Boolean {
        logWarningOnceOnly()
        return super.addMetadata(key, value)
    }

    override fun applyLogLevelMap(logLevelMap: LogLevelMap): Boolean {
        logWarningOnceOnly()
        return super.applyLogLevelMap(logLevelMap)
    }

    override fun close() = Unit
    override fun isNoOp(): Boolean = true
}
