/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend.given

import io.spine.logging.jvm.backend.LogData
import io.spine.logging.jvm.backend.LoggerBackend
import java.util.logging.Level

/**
 * A logger backend that captures all [LogData] instances.
 *
 * This class is mutable and not thread safe.
 *
 * @see <a href="http://rb.gy/r6jjw">Original Java code of Google Flogger</a>
 *   for historical context.
 */
class MemoizingLoggerBackend(
    override val loggerName: String = "com.example.MyClass"
) : LoggerBackend() {

    private var minLevel = Level.INFO
    private val mutableLogged: MutableList<LogData> = ArrayList()

    /**
     * [LogData] entries captured by this backend.
     */
    val logged: List<LogData> get() = mutableLogged

    /**
     * Number of [LogData] entries captured by this backend.
     */
    val loggedCount: Int get() = mutableLogged.size

    /**
     * The last [LogData] captured by this backend.
     *
     * @throws NoSuchElementException if there are no entries
     */
    val lastLogged: LogData get() = mutableLogged.last()

    /**
     * The first [LogData] captured by this backend.
     *
     * @throws NoSuchElementException if there are no entries
     */
    val firstLogged: LogData get() = mutableLogged.first()

    /**
     * Sets the current level of this backend.
     */
    fun setLevel(level: Level) {
        minLevel = level
    }

    override fun isLoggable(level: Level) = level.intValue() >= minLevel.intValue()

    override fun log(data: LogData) {
        mutableLogged.add(data)
    }

    override fun handleError(error: RuntimeException, badData: LogData) = throw error
}

