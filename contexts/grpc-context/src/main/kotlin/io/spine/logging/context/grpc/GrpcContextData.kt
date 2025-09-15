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

package io.spine.logging.context.grpc

import io.spine.logging.Level
import io.spine.logging.LoggingScope
import io.spine.logging.context.ContextMetadata
import io.spine.logging.context.LogLevelMap
import io.spine.logging.context.ScopeItem
import io.spine.logging.context.ScopeType
import io.spine.logging.context.Tags
import java.util.concurrent.atomic.AtomicReference

/**
 * A mutable thread-safe holder for context-scoped logging information.
 *
 * See original Java for historical context.
 */
internal class GrpcContextData(
    parent: GrpcContextData?,
    scopeType: ScopeType?,
    private val provider: GrpcContextDataProvider,
) {

    private val scopes: ScopeItem? = ScopeItem.addScope(parent?.scopes, scopeType)

    private abstract class ScopedReference<T>(initialValue: T?) {
        private val value = AtomicReference(initialValue)
        fun get(): T? = value.get()
        fun mergeFrom(delta: T?) {
            if (delta != null) {
                var current: T?
                do {
                    current = get()
                } while (!value.compareAndSet(current, current?.let { merge(it, delta) } ?: delta))
            }
        }
        protected abstract fun merge(current: T, delta: T): T
    }

    private val parentTags: Tags? = parent?.tagRef?.get()
    private val tagRef = object : ScopedReference<Tags>(parentTags) {
        override fun merge(current: Tags, delta: Tags): Tags = current.merge(delta)
    }

    private val parentMetadata: ContextMetadata? = parent?.metadataRef?.get()
    private val metadataRef = object : ScopedReference<ContextMetadata>(parentMetadata) {
        override fun merge(current: ContextMetadata, delta: ContextMetadata): ContextMetadata =
            current.concatenate(delta)
    }

    private val parentLogLevelMap: LogLevelMap? = parent?.logLevelMapRef?.get()

    private val logLevelMapRef = object : ScopedReference<LogLevelMap>(parentLogLevelMap) {
        override fun merge(current: LogLevelMap, delta: LogLevelMap): LogLevelMap =
            current.merge(delta)
    }

    fun addTags(tags: Tags?) {
        tagRef.mergeFrom(tags)
    }

    fun addMetadata(metadata: ContextMetadata?) {
        metadataRef.mergeFrom(metadata)
    }

    fun applyLogLevelMap(logLevelMap: LogLevelMap?) {
        if (logLevelMap != null) {
            provider.setLogLevelMapFlag()
            logLevelMapRef.mergeFrom(logLevelMap)
        }
    }

    fun getMappedLevel(loggerName: String): Level? {
        val map = logLevelMapRef.get() ?: return null
        return map.getLevel(loggerName)
    }

    companion object {
        fun getTagsFor(context: GrpcContextData?): Tags {
            if (context != null) {
                val tags = context.tagRef.get()
                if (tags != null) return tags
            }
            return Tags.empty()
        }

        fun getMetadataFor(context: GrpcContextData?): ContextMetadata {
            if (context != null) {
                val metadata = context.metadataRef.get()
                if (metadata != null) return metadata
            }
            return ContextMetadata.empty()
        }

        fun shouldForceLoggingFor(
            context: GrpcContextData?,
            loggerName: String,
            level: Level
        ): Boolean {
            if (context != null) {
                val map = context.logLevelMapRef.get()
                if (map != null) {
                    return map.getLevel(loggerName).value <= level.value
                }
            }
            return false
        }

        fun lookupScopeFor(contextData: GrpcContextData?, type: ScopeType): LoggingScope? =
            if (contextData != null) ScopeItem.lookup(contextData.scopes, type) else null
    }
}
