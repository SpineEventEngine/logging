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
import io.spine.logging.Level
import io.spine.logging.LoggingScope
import io.spine.logging.context.ContextDataProvider
import io.spine.logging.context.ContextMetadata
import io.spine.logging.context.ScopeType
import io.spine.logging.context.ScopedLoggingContext
import io.spine.logging.context.Tags

/**
 * A gRPC-based implementation of ContextDataProvider.
 */
public class GrpcContextDataProvider : ContextDataProvider() {

    public companion object {
        // For use by GrpcScopedLoggingContext (same package).
        internal fun getContextKey(): Context.Key<GrpcContextData> = KeyHolder.GRPC_SCOPE
        internal fun currentContext(): GrpcContextData? = getContextKey().get()
    }

    // Lazily created API singleton
    @Volatile private var configInstance: GrpcScopedLoggingContext? = null

    // Flag to know if any context applied a log level map
    @Volatile private var hasLogLevelMap: Boolean = false

    internal fun setLogLevelMapFlag() { hasLogLevelMap = true }

    public override fun getContextApiSingleton(): ScopedLoggingContext {
        var result = configInstance
        if (result == null) {
            result = GrpcScopedLoggingContext(this)
            configInstance = result
        }
        return result
    }

    public override fun getTags(): Tags = GrpcContextData.getTagsFor(currentContext())

    public override fun getMetadata(): ContextMetadata =
        GrpcContextData.getMetadataFor(currentContext())

    public override fun getScope(type: ScopeType): LoggingScope? =
        GrpcContextData.lookupScopeFor(currentContext(), type)

    public override fun shouldForceLogging(
        loggerName: String,
        level: Level,
        isEnabledByLevel: Boolean
    ): Boolean =
        hasLogLevelMap && GrpcContextData.shouldForceLoggingFor(currentContext(), loggerName, level)

    @Suppress("ReturnCount")
    public override fun getMappedLevel(loggerName: String): Level? {
        if (!hasLogLevelMap) return null
        val context = currentContext() ?: return null
        return context.getMappedLevel(loggerName)
    }

    /**
     * Lazy holder for `Context.Key`.
     */
    private object KeyHolder {
        val GRPC_SCOPE: Context.Key<GrpcContextData> = Context.key("Logging gRPC scope")
    }
}
