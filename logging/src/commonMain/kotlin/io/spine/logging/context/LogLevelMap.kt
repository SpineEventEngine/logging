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
import io.spine.logging.toLoggerName
import kotlin.reflect.KClass

/**
 * A hierarchical mapping from logger name to [Level] used to override the configured log level.
 *
 * This can be used for debugging or to provide more detailed log output for specific
 * conditions, such as when a command-line argument is passed.
 */
public interface LogLevelMap {

    /**
     * Returns the log level for the specified logger.
     *
     * The logger name is matched to an entry in the map, or the nearest parent in
     * the naming hierarchy. If the given logger name is not matched to any entry,
     * the default value is returned.
     */
    public fun levelOf(loggerName: String): Level

    /**
     * Returns the union of this map with the given map.
     *
     * Logging is enabled in the merged map IFF it was enabled in one of
     * the maps it was created from.
     */
    public fun merge(other: LogLevelMap): LogLevelMap

    public interface Builder {
        public fun add(level: Level, vararg classes: KClass<*>): Builder
        public fun add(level: Level, vararg packageNames: String): Builder
        public fun setDefault(level: Level): Builder
        public fun build(): LogLevelMap
    }

    public companion object {

        /**
         * Creates a builder for a new log level map.
         */
        public fun builder(): Builder = LoggingContextFactory.levelMapBuilder()

        /**
         * Creates a new instance with the given [level values][map] and [defaultLevel].
         */
        @JvmOverloads
        public fun create(
            map: Map<String, Level> = mapOf(),
            defaultLevel: Level = Level.OFF
        ): LogLevelMap =
            LoggingContextFactory.levelMap(map, defaultLevel)
    }
}

/**
 * Obtains a level of logging for the given class.
 */
public fun LogLevelMap.levelOf(cls: KClass<*>): Level = levelOf(cls.toLoggerName())

/**
 * Builds a new [LogLevelMap] by populating a newly created [LogLevelMap.Builder]
 * using providing [builderAction].
 */
public fun logLevelMap(builderAction: LogLevelMap.Builder.() -> Unit): LogLevelMap {
    val builder = LogLevelMap.builder()
    builderAction(builder)
    return builder.build()
}
