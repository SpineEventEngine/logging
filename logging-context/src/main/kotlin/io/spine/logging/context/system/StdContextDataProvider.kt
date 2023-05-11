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
import com.google.common.flogger.backend.Metadata
import com.google.common.flogger.context.ContextDataProvider
import com.google.common.flogger.context.ScopeType
import com.google.common.flogger.context.ScopedLoggingContext
import com.google.common.flogger.context.Tags
import io.spine.logging.context.system.StdContextData.Companion.current
import io.spine.logging.context.system.StdContextData.Companion.shouldForceLoggingFor
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

    override fun getContextApiSingleton(): ScopedLoggingContext {
        var result: ScopedLoggingContext? = scopedLoggingContext
        if (result == null) {
            scopedLoggingContext = StdScopedLoggingContext(this)
            result = scopedLoggingContext
        }
        return result!!
    }

    override fun shouldForceLogging(
        loggerName: String,
        level: Level,
        isEnabledByLevel: Boolean
    ): Boolean {
        return hasLogLevelMap && shouldForceLoggingFor(loggerName, level.toLevel())
    }

    override fun getTags(): Tags = StdContextData.tagsFor(current())

    override fun getMetadata(): Metadata = StdContextData.metadataFor(current())

    override fun getScope(type: ScopeType?): LoggingScope? {
        // TODO: Implement scope lookup.
        return super.getScope(type)
    }

    internal fun setLogLevelMapFlag() {
        hasLogLevelMap = true
    }

    internal companion object {
        private var scopedLoggingContext: StdScopedLoggingContext? = null
    }
}
