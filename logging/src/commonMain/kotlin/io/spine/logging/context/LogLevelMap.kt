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

package io.spine.logging.context

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.Level
import io.spine.logging.toLoggerName
import kotlin.reflect.KClass

/**
 * A hierarchical mapping from logger name to [Level] used to override the configured log
 * level during debugging. This class is designed to allow efficient (i.e., zero-allocation)
 * resolution of the log level for a given logger.
 *
 * This class is immutable and thread-safe.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/LogLevelMap.java">
 *     Original Java code</a> for historical context.
 */
@Immutable
@ThreadSafe
public class LogLevelMap private constructor(map: Map<String, Level>, defaultLevel: Level) {

    private val trie: SegmentTrie<Level> = SegmentTrie.create(map, '.', defaultLevel)

    /**
     * Returns the log level for the specified logger, matching the [loggerName] to an entry
     * in the map, or the nearest parent in the naming hierarchy. If the given [loggerName] is
     * invalid, the default value is returned.
     */
    public fun getLevel(loggerName: String): Level {
        return trie.find(loggerName)
    }

    public fun levelOf(loggerName: String): Level = getLevel(loggerName)

    /**
     * Returns the union of this map with the given map. Logging is enabled in the merged map
     * if-and-only-if it was enabled in one of the maps it was created from.
     */
    public fun merge(other: LogLevelMap): LogLevelMap {
        val thisMap = trie.getEntryMap()
        val otherMap = other.trie.getEntryMap()

        // HashMap/HashSet is fine because iteration order is unimportant for creating a SegmentTrie.
        val mergedMap = HashMap<String, Level>()
        val allKeys = HashSet(thisMap.keys)
        allKeys.addAll(otherMap.keys)
        for (key in allKeys) {
            when {
                !otherMap.containsKey(key) -> mergedMap[key] = thisMap[key]!!
                !thisMap.containsKey(key) -> mergedMap[key] = otherMap[key]!!
                else -> mergedMap[key] = min(thisMap[key]!!, otherMap[key]!!)
            }
        }

        val defaultLevel = min(trie.getDefaultValue(), other.trie.getDefaultValue())
        return create(mergedMap, defaultLevel)
    }

    /**
     * Builder for log level map which uses type safe class/package keys (but requires that they be
     * present in the JVM at the time the map is created). To set up a [LogLevelMap] with only
     * class/package names, use [LogLevelMap.create] with a map and level or just a map.
     */
    public class Builder internal constructor() {

        private val map = mutableMapOf<String, Level>()
        private var defaultLevel = Level.OFF

        /**
         * Associates the given [loggerName] with the logging [level].
         *
         * @throws IllegalArgumentException if the entry for [loggerName] already exists.
         */
        public fun put(loggerName: String, level: Level) {
            val alreadyMapped = map.put(loggerName, level)
            require (alreadyMapped == null) {
                "Duplicate entry for class/package: `$loggerName`, level: `$alreadyMapped`."
            }
        }

        /**
         * Adds the given classes at the specified log level.
         *
         * @param level The logging level to assign for the given [classes]
         * @param classes The classes to assign the logging [level]. Each class must have
         *   a [qualified name][KClass.qualifiedName].
         * @throws IllegalArgumentException if one of the [classes] does not have
         *   a [qualified name][KClass.qualifiedName].
         */
        @CanIgnoreReturnValue
        public fun add(level: Level, vararg classes: KClass<*>): Builder {
            for (cls in classes) {
                val name = cls.qualifiedName
                require(name != null) {
                    "Cannot add the class `${cls}` because it does not have" +
                            " a qualified name (it may be local or anonymous)."
                }
                put(name, level)
            }
            return this
        }

        /**
         * Adds the given package names at the specified level.
         *
         * @param level The logging level to assign for the given [packageNames].
         * @param packageNames The names of the packages to assign the logging [level]
         */
        @CanIgnoreReturnValue
        public fun add(level: Level, vararg packageNames: String): Builder {
            for (pkg in packageNames) {
                put(pkg, level)
            }
            return this
        }

        /**
         * Sets the default log level (use [Level.OFF] to disable).
         */
        @CanIgnoreReturnValue
        public fun setDefault(level: Level): Builder {
            this.defaultLevel = level
            return this
        }

        public fun build(): LogLevelMap = create(map, defaultLevel)
    }

    public companion object {

        /**
         * Returns a new builder for constructing a [LogLevelMap].
         */
        @JvmStatic
        public fun builder(): Builder = Builder()

        /**
         * Returns an empty [LogLevelMap] with a single default level which
         * will apply to all loggers.
         */
        @JvmStatic
        public fun create(level: Level): LogLevelMap =
            create(mapOf(), level)

        /**
         * Returns a [LogLevelMap] whose entries correspond to the given map, and with the default
         * value of [Level.OFF]. The keys of the map must all be valid dot-separated logger names,
         * and the values cannot be `null`.
         */
        @JvmStatic
        public fun create(map: Map<String, Level>): LogLevelMap =
            create(map, Level.OFF)

        /**
         * Returns a [LogLevelMap] whose entries correspond to the given map.
         *
         * The keys of the map must all be valid dot-separated logger names, and
         * neither the values, nor the default value, can be `null`.
         */
        @JvmStatic
        @Suppress("UseRequire")
        public fun create(map: Map<String, Level>, defaultLevel: Level): LogLevelMap {
            for (e in map.entries) {
                val name = e.key
                require(
                    (name.startsWith(".")
                            || name.endsWith(".")
                            || name.contains(".."))
                        .not()
                ) {
                    "Invalid logger name: `$name`."
                }
            }
            return LogLevelMap(map, defaultLevel)
        }
    }
}

private fun min(a: Level, b: Level): Level =
    if (a.value <= b.value) a else b

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
