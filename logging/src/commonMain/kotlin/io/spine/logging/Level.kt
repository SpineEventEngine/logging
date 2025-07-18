/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging

/**
 * Level of logging in an application.
 *
 * Implemented as data class rather than enum to allow for custom logging level values.
 * Please see the companion object for the standard levels.
 *
 * ## Adding custom levels
 *
 * Adding a custom logging level could be done like this:
 *
 * ```kotlin
 * public object MyLoggingLevels {
 *    public val CONFIG: Level = Level("CONFIG", 700)
 *    ...
 * }
 * ```
 * You may also want to add an extension function for the [Logger] class to
 * use thew new level:
 * ```
 * public fun <API: LoggingApi<API>> Logger<API>.atConfig(): API = at(MyLoggingLevels.CONFIG)
 * ```
 * If the new logging level needs to be converted to a level of underlying logging backend
 * a [LevelConverter] must be [registered][LevelConverter.register] prior to
 * performing the [conversion][LevelConverter.convert].
 *
 * @see LevelConverter
 */
public data class Level(
    val name: String,
    val value: Int
) {

    /**
     * Popular logging levels.
     *
     * Name properties use the words more popular among logging frameworks and implementations.
     * Level values repeat those from `java.util.logging.Level` for easier compatibility.
     */
    public companion object {
        public val OFF: Level = Level("OFF", Int.MAX_VALUE)
        public val FATAL: Level = Level("FATAL", 2000)
        public val ERROR: Level = Level("ERROR", 1000)
        public val WARNING: Level = Level("WARNING", 900)
        public val INFO: Level = Level("INFO", 800)
        public val DEBUG: Level = Level("DEBUG", 500)
        public val TRACE: Level = Level("TRACE", 400)
        public val ALL: Level = Level("ALL", Int.MIN_VALUE)
    }
}

/**
 * Compares the levels using their [values][Level.value].
 */
public operator fun Level.compareTo(other: Level): Int = value.compareTo(other.value)
