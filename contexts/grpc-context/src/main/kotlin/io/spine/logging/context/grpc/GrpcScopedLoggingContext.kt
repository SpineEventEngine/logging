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

package io.spine.logging.context.grpc

import io.grpc.Context
import io.spine.logging.MetadataKey
import io.spine.logging.context.ContextMetadata
import io.spine.logging.context.LogLevelMap
import io.spine.logging.context.ScopeType
import io.spine.logging.context.ScopedLoggingContext
import io.spine.logging.context.Tags
import io.spine.logging.util.Checks.checkNotNull

/**
 * A gRPC-based implementation of ScopedLoggingContext.
 */
internal class GrpcScopedLoggingContext(private val provider: GrpcContextDataProvider) :
    ScopedLoggingContext() {

    override fun newContext(): Builder = newBuilder(null)

    public override fun newContext(scopeType: ScopeType?): Builder = newBuilder(scopeType)

    private fun newBuilder(scopeType: ScopeType?): Builder = object : Builder() {
        override fun install(): AutoCloseable {
            val newContextData =
                GrpcContextData(GrpcContextDataProvider.currentContext(), scopeType, provider)
            newContextData.addTags(getTags())
            newContextData.addMetadata(getMetadata())
            newContextData.applyLogLevelMap(getLogLevelMap())
            return installContextData(newContextData)
        }
    }

    private companion object {

        private fun installContextData(newContextData: GrpcContextData): AutoCloseable {
            val newGrpcContext =
                Context.current().withValue(GrpcContextDataProvider.getContextKey(), newContextData)
            @Suppress("MustBeClosedChecker")
            val prev = newGrpcContext.attach()
            return AutoCloseable { newGrpcContext.detach(prev) }
        }
    }

    override fun addTags(tags: Tags): Boolean {
        checkNotNull(tags, "tags")
        val context = GrpcContextDataProvider.currentContext()
        if (context != null) {
            context.addTags(tags)
            return true
        }
        return false
    }

    override fun <T : Any> addMetadata(key: MetadataKey<T>, value: T): Boolean {
        val metadata = ContextMetadata.singleton(key, value)
        val context = GrpcContextDataProvider.currentContext()
        if (context != null) {
            context.addMetadata(metadata)
            return true
        }
        return false
    }

    override fun applyLogLevelMap(logLevelMap: LogLevelMap): Boolean {
        checkNotNull(logLevelMap, "log level map")
        val context = GrpcContextDataProvider.currentContext()
        if (context != null) {
            context.applyLogLevelMap(logLevelMap)
            return true
        }
        return false
    }
}
