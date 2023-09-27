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

package io.spine.logging.context.system

import io.spine.logging.flogger.LoggingScope
import io.spine.logging.flogger.backend.Metadata
import io.spine.logging.flogger.context.ContextDataProvider
import io.spine.logging.flogger.context.ScopeType
import io.spine.logging.flogger.context.ScopedLoggingContext
import io.spine.logging.flogger.context.Tags
import io.spine.logging.context.system.StdContextData.Companion.shouldForceLoggingFor
import io.spine.logging.context.toMap
import io.spine.logging.toLevel
import java.util.logging.Level

/**
 * A [ContextDataProvider] providing basic support of [ScopedLoggingContext]
 * similar to that provided by gRPC logging context from Flogger.
 *
 * Loaded via [ServiceLoader][java.util.ServiceLoader] by Flogger runtime.
 */
public class StdContextDataProvider: ContextDataProvider() {

    @Volatile
    private var hasLogLevelMap: Boolean = false

    override fun getContextApiSingleton(): ScopedLoggingContext = serving(this)

    override fun shouldForceLogging(
        loggerName: String,
        level: Level,
        isEnabledByLevel: Boolean
    ): Boolean {
        return hasLogLevelMap && shouldForceLoggingFor(loggerName, level.toLevel())
    }

    override fun getTags(): Tags = StdContextData.tags()

    override fun getMetadata(): Metadata = StdContextData.metadata()

    override fun getScope(type: ScopeType): LoggingScope? = StdContextData.lookupScopeFor(type)

    /**
     * Sets the flag to enable checking for a log level map after one
     * is set for the first time.
     */
    internal fun setLogLevelMapFlag() {
        hasLogLevelMap = true
    }

    private companion object {
        private var context: StdScopedLoggingContext? = null

        fun serving(provider: StdContextDataProvider): ScopedLoggingContext {
            if (context == null) {
                context = StdScopedLoggingContext(provider)
            }
            return context!!
        }
    }
}

/**
 * A [ScopedLoggingContext] which creates contexts based on [StdContextData].
 */
private class StdScopedLoggingContext(
    private val provider: StdContextDataProvider
) : ScopedLoggingContext() {

    override fun newContext(): Builder = BuilderImpl(null)

    override fun newContext(scopeType: ScopeType): Builder = BuilderImpl(scopeType)

    /**
     * A [ScopedLoggingContext.Builder] which creates a new [StdContextData] and
     * installs it.
     */
    inner class BuilderImpl(private val scopeType: ScopeType?) : Builder() {

        override fun install(): LoggingContextCloseable {
            val newContextData = StdContextData(scopeType, provider).apply {
                addTags(tags)
                addMetadata(metadata)
                applyLogLevelMap(logLevelMap.toMap())
            }
            return install(newContextData)
        }

        private fun install(newData: StdContextData): LoggingContextCloseable {
            val prev = newData.attach()
            return LoggingContextCloseable { newData.detach(prev) }
        }
    }
}
