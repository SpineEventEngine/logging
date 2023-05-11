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

import com.google.common.flogger.LoggingScope
import com.google.common.flogger.context.ContextMetadata
import com.google.common.flogger.context.ScopeType
import com.google.common.flogger.context.ScopedLoggingContext.ScopeList
import com.google.common.flogger.context.Tags
import io.spine.logging.Level
import io.spine.logging.context.LogLevelMap

internal class StdContextData(
    scopeType: ScopeType?,
    private val provider: StdContextDataProvider
) {
    private val scopes: ScopeList?
    private val tagRef: ScopedReference<Tags>
    private val metadataRef: ScopedReference<ContextMetadata>
    private val logLevelMapRef: ScopedReference<LogLevelMap>

    init {
        val parent = current
        scopes = ScopeList.addScope(parent?.scopes, scopeType)
        tagRef = object : ScopedReference<Tags>(parent?.tagRef?.get()) {
            override fun merge(current: Tags, delta: Tags): Tags =
                current.merge(delta)
        }
        metadataRef = object : ScopedReference<ContextMetadata>(parent?.metadataRef?.get()) {
            override fun merge(current: ContextMetadata, delta: ContextMetadata): ContextMetadata =
                current.concatenate(delta)
        }
        logLevelMapRef = object : ScopedReference<LogLevelMap>(parent?.logLevelMapRef?.get()) {
            override fun merge(current: LogLevelMap, delta: LogLevelMap): LogLevelMap =
                current.merge(delta)
        }
    }

    fun addTags(tags: Tags?) =
        tagRef.mergeFrom(tags)

    fun addMetadata(metadata: ContextMetadata?) =
        metadataRef.mergeFrom(metadata)

    fun applyLogLevelMap(map: LogLevelMap?) {
        if (map != null) {
            provider.setLogLevelMapFlag()
            logLevelMapRef.mergeFrom(map)
        }
    }

    fun attach(): StdContextData? {
        val prev = current
        holder.set(this)
        return prev
    }

    fun detach(prev: StdContextData?) {
        holder.set(prev)
    }

    companion object {

        private val holder: ThreadLocal<StdContextData> by lazy {
            ThreadLocal()
        }

        private val current: StdContextData?
            get() = holder.get()

        fun tags(): Tags {
            current?.let {
                it.tagRef.get()?.let { tags ->
                    return tags
                }
            }
            return Tags.empty()
        }

        fun metadata(): ContextMetadata {
            current?.let {
                it.metadataRef.get()?.let { metadata ->
                    return metadata
                }
            }
            return ContextMetadata.none()
        }

        fun shouldForceLoggingFor(loggerName: String, level: Level): Boolean {
            current?.let {
                it.logLevelMapRef.get()?.let { map ->
                    val levelFromMap = map.levelOf(loggerName)
                    return levelFromMap.value <= level.value
                }
            }
            return false
        }

        fun lookupScopeFor(type: ScopeType): LoggingScope? =
            current?.let { ScopeList.lookup(it.scopes, type) }
    }
}
