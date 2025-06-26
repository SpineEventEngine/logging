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

package io.spine.logging.testing

import io.spine.logging.Level
import io.spine.logging.toJavaLogging
import io.spine.logging.toLevel
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Intercepts logging records of the logger with the given name.
 */
public class JulRecorder(loggerName: String, minLevel: Level): Recorder(minLevel) {

    /**
     * The logger obtained for the given name.
     */
    private val logger: Logger = Logger.getLogger(loggerName)

    /**
     * The handler, which remembers log records and performs assertions.
     */
    private var handler: RecordingHandler? = null

    /**
     * The logger, which performs actual publishing for the [logger].
     *
     * Is `null` before the [start] or after [stop].
     */
    private var publishingLogger: Logger? = null

    override fun start() {
        handler = RecordingHandler()
        publishingLogger = publishingLoggerOf(logger)
        publishingLogger?.addHandler(handler)
    }

    override fun stop() {
        if (handler == null) {
            return
        }
        publishingLogger?.removeHandler(handler)
        handler = null
        publishingLogger = null
    }

    /**
     * Accumulates [records] with the [minLevel] or higher.
     */
    private inner class RecordingHandler: Handler() {

        init {
            level = minLevel.toJavaLogging()
        }

        override fun publish(record: LogRecord?) {
            record?.let {
                append(JulLogData(it))
            }
        }

        override fun flush(): Unit = Unit
        override fun close(): Unit = clear()
    }
}

/**
 * Obtains the logger responsible for publishing the logging records
 * produced by the given [logger].
 */
private fun publishingLoggerOf(logger: Logger): Logger {
    return if (!logger.useParentHandlers || logger.parent == null) {
        logger
    } else {
        publishingLoggerOf(logger.parent)
    }
}

/**
 * Implements [LogData] by wrapping over [LogRecord].
 */
private data class JulLogData(private val record: LogRecord): LogData {
    override val level: Level = record.level.toLevel()
    override val message: String = record.message
    override val throwable: Throwable? = record.thrown
    override fun equals(other: Any?): Boolean {
        return if (other is JulLogData) {
            record == other.record
        } else {
            false
        }
    }
    override fun hashCode(): Int = record.hashCode()
}
