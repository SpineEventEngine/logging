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

package io.spine.logging.context.std

import io.spine.logging.Level
import io.spine.logging.compareTo
import io.spine.logging.context.LogLevelMap
import io.spine.logging.jvm.LoggingScope
import io.spine.logging.jvm.context.ContextMetadata
import io.spine.logging.jvm.context.ScopeType
import io.spine.logging.jvm.context.ScopedLoggingContext.ScopeList
import io.spine.logging.jvm.context.Tags

/**
 * The data of a scoped logging context with merging capabilities when
 * contexts are nested.
 *
 * @param scopeType The type of the scope to be created, or `null` if no type is required.
 * @constructor Creates an instance taking initial values from the currently installed context data.
 *   If there is no current logging context, it initializes the instance with `null`s.
 */
internal class StdContextData(scopeType: ScopeType?) {

    internal val scopes: ScopeList?
    internal val tagRef: ScopedReference<Tags>
    internal val metadataRef: ScopedReference<ContextMetadata>
    internal val logLevelMapRef: ScopedReference<LogLevelMap>

    init {
        val parent = CurrentStdContext.data
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

    /**
     * Adds tags to the current context data.
     *
     * If `null` is passed, no action is taken.
     */
    fun addTags(tags: Tags?) = tagRef.mergeFrom(tags)

    /**
     * Concatenates given metadata with the one held in this context
     * data instance.
     *
     * If `null` is passed, no action is taken.
     */
    fun addMetadata(metadata: ContextMetadata?) = metadataRef.mergeFrom(metadata)

    /**
     * Merges the given map with the one held in this context data instance.
     *
     * If `null` is passed, no action is taken.
     */
    fun applyLogLevelMap(map: LogLevelMap?) {
        map?.let {
            StdContextDataProvider.hasLogLevelMap = true
            logLevelMapRef.mergeFrom(it)
        }
    }
}

/**
 * Holds the current context data in [ThreadLocal].
 */
internal object CurrentStdContext {

    /**
     * Contains a currently installed instance of context data or `null`,
     * if no context is installed.
     */
    private val holder: ThreadLocal<StdContextData> by lazy {
        ThreadLocal()
    }

    /**
     * Obtains the currently stored value of the context data.
     *
     * *Implementation note:* obtaining the value must be done via
     * `get() = holder.get()` rather than just `= holder.get()` which means
     * setting the currently stored value (`null`) during instance construction.
     */
    internal val data: StdContextData? get() = holder.get()

    /**
     * Obtains the tags of the current context or [Tags.empty] if no
     * context is installed.
     */
    fun tags(): Tags {
        data?.let {
            it.tagRef.get()?.let { tags ->
                return tags
            }
        }
        return Tags.empty()
    }

    /**
     * Obtains metadata of the current context or [ContextMetadata.none]
     * if no context is installed.
     */
    fun metadata(): ContextMetadata {
        data?.let {
            it.metadataRef.get()?.let { metadata ->
                return metadata
            }
        }
        return ContextMetadata.none()
    }

    /**
     * Tells if the logging should be forced for the logger with
     * the given name and the level.
     *
     * Returns `false` if no context is installed.
     */
    fun shouldForceLoggingFor(loggerName: String, level: Level): Boolean {
        data?.let {
            it.logLevelMapRef.get()?.let { map ->
                val levelFromMap = map.levelOf(loggerName)
                val result = level >= levelFromMap
                return result
            }
        }
        return false
    }

    /**
     * Obtains a custom level set for the logger with the given name via [LogLevelMap],
     * if it exists.
     *
     * @param loggerName The name of the logger.
     * @return The custom level or `null` if there is no map, or the map does not affect
     *   the level of the given logger.
     */
    fun mappedLevelOf(loggerName: String): Level? {
        return data?.let {
            it.logLevelMapRef.get()?.levelOf(loggerName)
        }
    }

    /**
     * Obtains a [LoggingScope] for the given type.
     *
     * @return the scope instance or `null` if there is no current context,
     *         or the current context data does not have scopes, or there
     *         is no logging scope with the requested type.
     */
    fun lookupScopeFor(type: ScopeType): LoggingScope? =
        data?.let { ScopeList.lookup(it.scopes, type) }

    /**
     * Installs the given [newData] as the current context data.
     */
    fun attach(newData: StdContextData?) {
        holder.set(newData)
    }
}
