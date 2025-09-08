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

package io.spine.logging.backend.jul.given

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.Level
import io.spine.logging.LogContext
import io.spine.logging.LogSite
import io.spine.logging.MetadataKey
import io.spine.logging.backend.LogData
import io.spine.logging.toLevel
import java.util.logging.Level as JLevel

/**
 * A mutable [LogData] fot testing backends and other log handling code.
 *
 * @see <a href="http://rb.gy/z2i0q">Original Java code of Google Flogger</a>
 *   for historical context.
 */
@Suppress("TooManyFunctions") // Many getters and setters.
internal class StubLogData : LogData {

    override var level: Level = Level.INFO
    private var _literalArgument: Any? = null
    override var timestampNanos = 0L
    override val metadata = StubMetadata()
    override var logSite: LogSite = LOG_SITE

    companion object {
        private const val LOGGER_NAME = "io.spine.LoggerName"
        private const val LOGGING_CLASS = "io.spine.FakeClass"
        private const val LOGGING_METHOD = "doAct"
        private const val LINE_NUMBER = 123
        private const val SOURCE_FILE = "src/io/spine/FakeClass.java"
        private val LOG_SITE = StubLogSite(LOGGING_CLASS, LOGGING_METHOD, LINE_NUMBER, SOURCE_FILE)
    }

    /**
     * Creates an instance with a single literal argument.
     */
    constructor(literalArgument: Any?) {
        this._literalArgument = literalArgument
    }

    @CanIgnoreReturnValue
    fun setTimestampNanos(timestampNanos: Long): StubLogData {
        this.timestampNanos = timestampNanos
        return this
    }

    @CanIgnoreReturnValue
    fun setLevel(level: Level): StubLogData {
        this.level = level
        return this
    }

    @CanIgnoreReturnValue
    fun setLevel(level: JLevel): StubLogData = setLevel(level.toLevel())

    @CanIgnoreReturnValue
    fun <T : Any> addMetadata(key: MetadataKey<T>, value: Any): StubLogData {
        metadata.add(key, key.cast(value)!!)
        return this
    }

    override val loggerName: String
        get() = LOGGER_NAME

    override fun wasForced(): Boolean {
        // Check explicit `TRUE` here because `findValue()` can return `null`.
        // That would fail unboxing.
        return metadata.findValue(LogContext.Key.WAS_FORCED) == true
    }

    override val literalArgument: Any?
        get() = _literalArgument
}

