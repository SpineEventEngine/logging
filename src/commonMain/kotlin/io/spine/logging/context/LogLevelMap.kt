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

package io.spine.logging.context

import io.spine.logging.Level
import kotlin.reflect.KClass

public interface LogLevelMap {

    public fun levelOf(loggerName: String): Level
    public fun merge(other: LogLevelMap): LogLevelMap

    public interface Builder {
        public fun add(level: Level, vararg classes: KClass<*>): Builder
        public fun add(level: Level, vararg packageNames: String): Builder
        public fun setDefault(level: Level): Builder
        public fun build(): LogLevelMap
    }

    public companion object {
        public fun builder(): Builder =
            LogLevelMapBuilderImpl()

        public fun create(map: Map<String, Level>, defaultLevel: Level): LogLevelMap =
            LogLevelMapImpl(map, defaultLevel)
    }
}

public fun logLevelMap(block: LogLevelMap.Builder.() -> Unit): LogLevelMap {
    val builder = LogLevelMap.builder()
    block(builder)
    return builder.build()
}

internal expect class LogLevelMapBuilderImpl(): LogLevelMap.Builder

internal expect class LogLevelMapImpl(map: Map<String, Level>, defaultLevel: Level): LogLevelMap
