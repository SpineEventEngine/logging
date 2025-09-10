/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.context.std

import io.spine.logging.Level
import io.spine.logging.LoggingScope
import io.spine.logging.backend.Metadata
import io.spine.logging.context.Tags
import io.spine.logging.context.toMap
import io.spine.logging.jvm.context.ContextDataProvider
import io.spine.logging.context.ScopeType
import io.spine.logging.jvm.context.ScopedLoggingContext

/**
 * A basic implementation of [ContextDataProvider].
 *
 * Loaded via [ServiceLoader][java.util.ServiceLoader] by the logging facade
 * in runtime.
 */
public class StdContextDataProvider: ContextDataProvider() {

    internal companion object {
        @Volatile
        internal var hasLogLevelMap: Boolean = false
    }

    override fun getContextApiSingleton(): ScopedLoggingContext = StdScopedLoggingContext

    override fun shouldForceLogging(
        loggerName: String,
        level: Level,
        isEnabledByLevel: Boolean
    ): Boolean {

        if (!hasLogLevelMap) {
            return false
        }

        return CurrentStdContext.shouldForceLoggingFor(loggerName, level)
    }

    override fun getMappedLevel(loggerName: String): Level? {
        if (!hasLogLevelMap) {
            return null
        }
        return CurrentStdContext.mappedLevelOf(loggerName)
    }

    override fun getTags(): Tags = CurrentStdContext.tags()

    override fun getMetadata(): Metadata = CurrentStdContext.metadata()

    override fun getScope(type: ScopeType): LoggingScope? = CurrentStdContext.lookupScopeFor(type)
}

/**
 * A [ScopedLoggingContext] singleton, which creates [AutoCloseable]
 * based on [StdContextData].
 */
private object StdScopedLoggingContext: ScopedLoggingContext() {

    override fun newContext(): Builder = BuilderImpl(null)

    override fun newContext(scopeType: ScopeType?): Builder = BuilderImpl(scopeType)

    override fun addTags(tags: Tags): Boolean {
        CurrentStdContext.data?.addTags(tags) ?: return false
        return true
    }

    /**
     * A [ScopedLoggingContext.Builder] which creates a new [StdContextData]
     * and installs it.
     */
    class BuilderImpl(private val scopeType: ScopeType?) : Builder() {

        override fun install(): AutoCloseable {
            val newContextData = StdContextData(scopeType)
            newContextData.apply {
                addTags(getTags())
                addMetadata(getMetadata())
                applyLogLevelMap(getLogLevelMap().toMap())
            }
            return install(newContextData)
        }

        private fun install(newData: StdContextData): AutoCloseable {
            val previousData = CurrentStdContext.data
            CurrentStdContext.attach(newData)
            return AutoCloseable { CurrentStdContext.attach(previousData) }
        }
    }
}
