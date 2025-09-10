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

package io.spine.logging.jvm.context

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.context.SegmentTrie
import java.util.*
import java.util.logging.Level

/**
 * A hierarchical mapping from logger name to [Level] used to override the configured log
 * level during debugging. This class is designed to allow efficient (i.e., zero-allocation)
 * resolution of the log level for a given logger.
 *
 * This class is immutable and thread-safe.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/LogLevelMap.java)
 * for historical context.
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

        private val map = HashMap<String, Level>()
        private var defaultLevel = Level.OFF

        private fun put(name: String, level: Level) {
            val alreadyMapped = map.put(name, level)
            require (alreadyMapped == null) {
                "Duplicate entry for class/package: `$name`, level: `$alreadyMapped`."
            }
        }

        /**
         * Adds the given classes at the specified log level.
         */
        @CanIgnoreReturnValue
        public fun add(level: Level, vararg classes: Class<*>): Builder {
            for (cls in classes) {
                put(cls.name, level)
            }
            return this
        }

        /**
         * Adds the given packages at the specified log level.
         */
        @CanIgnoreReturnValue
        public fun add(level: Level, vararg packages: Package): Builder {
            for (pkg in packages) {
                put(pkg.name, level)
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
            create(Collections.emptyMap(), level)

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
    if (a.intValue() <= b.intValue()) a else b
