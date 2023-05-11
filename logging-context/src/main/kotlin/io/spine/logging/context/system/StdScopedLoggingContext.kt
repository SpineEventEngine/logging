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

import com.google.common.flogger.context.ScopeType
import com.google.common.flogger.context.ScopedLoggingContext
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable

import io.spine.logging.context.toMap

internal class StdScopedLoggingContext(
    private val provider: StdContextDataProvider
) : ScopedLoggingContext() {

    override fun newContext(): Builder = newBuilder(null)

    override fun newContext(scopeType: ScopeType): Builder = newBuilder(scopeType)

    private fun newBuilder(scopeType: ScopeType?): Builder {
        return object : Builder() {
            override fun install(): LoggingContextCloseable {
                val newContextData = StdContextData(scopeType, provider).also {
                    it.addTags(tags)
                    it.addMetadata(metadata)
                    it.applyLogLevelMap(logLevelMap.toMap())
                }
                return installContextData(newContextData)
            }
        }
    }
}

private fun installContextData(newContextData: StdContextData): LoggingContextCloseable {
    val prev = newContextData.attach()
    return LoggingContextCloseable { newContextData.detach(prev) }
}
