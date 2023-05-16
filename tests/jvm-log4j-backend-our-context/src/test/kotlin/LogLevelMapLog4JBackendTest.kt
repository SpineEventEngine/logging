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

import given.map.CustomLoggingLevel.TRACE
import io.spine.logging.Level
import io.spine.logging.Level.Companion.ALL
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.OFF
import io.spine.logging.Level.Companion.WARNING
import io.spine.logging.context.BaseLogLevelMapTest
import io.spine.testing.logging.LogData
import io.spine.testing.logging.Recorder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.Level as L4jLevel

/**
 * This is a non-abstract integration test of [LogLevelMap][io.spine.logging.context.LogLevelMap]
 * executed in the project in which logging backend is based on Log4J by Flogger.
 *
 * Please see `build.gradle.kts` of this module for the details.
 */
internal class LogLevelMapLog4JBackendTest: BaseLogLevelMapTest() {

    override fun createRecorder(loggerName: String, minLevel: Level): Recorder =
        Log4JRecorder(loggerName, minLevel)
}

/**
 * Records logging events produced by Log4J loggers.
 */
private class Log4JRecorder(loggerName: String, minLevel: Level): Recorder(minLevel) {

    private val logger: Logger = (LogManager.getLogger(loggerName) as Logger)
    private var prevRootLevel: L4jLevel? = null
    private var prevLevel: L4jLevel? = null
    private var appender: Appender? = null

    override fun start() {
        logger.apply {
            prevLevel = level
            level = minLevel.toLog4J()
        }
        appender = object: AbstractAppender(logger.name, null, null, true, null) {
            override fun append(event: LogEvent) {
                val data = Log4JLogData(event)
                append(data)
            }
        }
        logger.addAppender(appender)

        // Set the root logger level to `INFO` to be at the same level as `java.util.logging`.
        // This is the assumption of JUL-based tests.
        val rootLogger = LogManager.getRootLogger() as Logger
        prevRootLevel = rootLogger.level
        rootLogger.level = org.apache.logging.log4j.Level.INFO
    }

    override fun stop() {
        prevLevel?.let {
            logger.level = it
            prevLevel = null
        }
        appender?.let {
            logger.removeAppender(it)
            appender = null
        }
        prevRootLevel?.let {
            (LogManager.getRootLogger() as Logger).level = prevRootLevel
            prevRootLevel = null
        }
    }
}

private fun Level.toLog4J(): L4jLevel {
    return when (this) {
        OFF -> L4jLevel.OFF
        ERROR -> L4jLevel.ERROR
        WARNING -> L4jLevel.WARN
        INFO -> L4jLevel.INFO
        DEBUG -> L4jLevel.DEBUG
        TRACE -> L4jLevel.TRACE
        ALL -> L4jLevel.ALL
        else -> L4jLevel.forName(this.name, this.value)
    }
}

private fun L4jLevel.toLevel(): Level {
    return when (this) {
        L4jLevel.OFF  -> OFF
        L4jLevel.ERROR -> ERROR
        L4jLevel.WARN -> WARNING
        L4jLevel.INFO -> INFO
        L4jLevel.DEBUG -> DEBUG
        L4jLevel.TRACE -> TRACE
        L4jLevel.ALL -> ALL
        else -> Level(name(), intLevel())
    }
}

private class Log4JLogData(val event: LogEvent): LogData {
    override val level: Level by lazy {
        event.level.toLevel()
    }
    override val message: String = event.message.formattedMessage
    override val throwable: Throwable? = event.thrown
}
