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

package io.spine.logging.context.tls

import io.spine.logging.context.toMap
import io.spine.logging.flogger.LoggingScope
import io.spine.logging.flogger.backend.Metadata
import io.spine.logging.flogger.context.ContextDataProvider
import io.spine.logging.flogger.context.ScopeType
import io.spine.logging.flogger.context.ScopedLoggingContext
import io.spine.logging.flogger.context.ScopedLoggingContext.LoggingContextCloseable
import io.spine.logging.flogger.context.Tags
import io.spine.logging.toLevel
import java.util.logging.Level as JLevel

/**
 * A [ThreadLocal]-based implementation of [ScopedLoggingContext].
 *
 * Loaded via [ServiceLoader][java.util.ServiceLoader] by the logging facade
 * in runtime.
 */
public class TlsContextDataProvider: ContextDataProvider() {

    internal companion object {
        @Volatile
        internal var hasLogLevelMap: Boolean = false
    }

    override fun getContextApiSingleton(): ScopedLoggingContext = TlsScopedLoggingContext

    override fun shouldForceLogging(
        loggerName: String,
        jLevel: JLevel,
        isEnabledByLevel: Boolean
    ): Boolean {

        if (!hasLogLevelMap) {
            return false
        }

        val level = jLevel.toLevel()
        return CurrentTlsContext.shouldForceLoggingFor(loggerName, level)
    }

    override fun getTags(): Tags = CurrentTlsContext.tags()

    override fun getMetadata(): Metadata = CurrentTlsContext.metadata()

    override fun getScope(type: ScopeType): LoggingScope? = CurrentTlsContext.lookupScopeFor(type)
}

/**
 * A [ScopedLoggingContext] singleton, which creates [LoggingContextCloseable]
 * based on [TlsContextData].
 */
private object TlsScopedLoggingContext: ScopedLoggingContext() {

    override fun newContext(): Builder = BuilderImpl(null)

    override fun newContext(scopeType: ScopeType): Builder = BuilderImpl(scopeType)

    /**
     * A [ScopedLoggingContext.Builder] which creates a new [TlsContextData]
     * and installs it.
     */
    class BuilderImpl(private val scopeType: ScopeType?) : Builder() {

        override fun install(): LoggingContextCloseable {
            val newContextData = TlsContextData(scopeType)
            newContextData.apply {
                addTags(tags)
                addMetadata(metadata)
                applyLogLevelMap(logLevelMap.toMap())
            }
            return install(newContextData)
        }

        private fun install(newData: TlsContextData): LoggingContextCloseable {
            val previousData = CurrentTlsContext.data
            CurrentTlsContext.attach(newData)
            return LoggingContextCloseable { CurrentTlsContext.attach(previousData) }
        }
    }
}
