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

import io.spine.logging.Level
import io.spine.logging.toJavaLogging
import java.util.logging.Handler
import java.util.logging.Level as JLevel
import java.util.logging.LogRecord
import java.util.logging.Logger

public fun checkLogging(
    loggerName: String,
    maxLevel: Level,
    block: Recorder.() -> Unit
) {
    val recorder = Recorder(loggerName, maxLevel.toJavaLogging())
    try {
        recorder.start()
        recorder.block()
    } finally {
        recorder.stop()
    }
}

public fun checkLogging(
    loggingClass: Class<*>,
    maxLevel: Level,
    block: Recorder.() -> Unit
) = checkLogging(loggingClass.name, maxLevel, block)

/**
 * Intercepts logging records of the associated class.
 */
open class Recorder(
    public val loggerName: String,
    public val level: JLevel
) {

    /**
     * The logger obtained by the given name.
     */
    private val logger: Logger = Logger.getLogger(loggerName)

    /**
     * The handler which remembers log records and performs assertions.
     */
    private var handler: RecordingHandler? = null

    /**
     * The logger which performs actual publishing for the [logger].
     *
     * Is `null` before the [start] or after [stop].
     */
    private var publishingLogger: Logger? = null

    /**
     * Contains log records collected so far.
     *
     * Is always empty before [start] and after [stop].
     */
    public val records: List<LogRecord>
        get() = handler?.records ?: emptyList()

    /**
     * Starts the recording.
     *
     * @see [stop]
     */
    fun start() {
        handler = RecordingHandler(level)
        publishingLogger = publishingLoggerOf(logger)
        publishingLogger?.addHandler(handler)
    }

    /**
     * Stops the recording.
     *
     * @see [start]
     */
    fun stop() {
        if (handler == null) {
            return
        }
        publishingLogger?.removeHandler(handler)
        handler = null
        publishingLogger = null
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
 * Accumulates [records] that have the specified [level][getLevel] or higher.
 */
private class RecordingHandler(level: JLevel): Handler() {

    init {
        setLevel(level)
    }

    private val mutableRecords = mutableListOf<LogRecord>()
    val records: List<LogRecord> get() = mutableRecords

    override fun publish(record: LogRecord?) {
        record?.let {
            mutableRecords.add(it)
        }
    }

    override fun flush() = Unit

    override fun close() = mutableRecords.clear()
}
