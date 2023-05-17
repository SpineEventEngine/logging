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

import given.map.CustomLoggingLevel.ANNOUNCEMENT
import given.map.CustomLoggingLevel.TRACE
import io.kotest.matchers.shouldBe
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
import kotlin.math.absoluteValue
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
        Log4jRecorder(loggerName, minLevel)

    init {
        should("Use levels converted to Log4J") {
            // In terms of Log4J lesser level means higher barrier for log records.
            // Therefore, the custom level of `ANNOUNCEMENT` defined in JUL terms as being
            // higher than `WARNING` should be less than `INFO` and `WARNING` when
            // converted to Log4J.
            (ANNOUNCEMENT.toLog4j() <= INFO.toLog4j()) shouldBe true
            (ANNOUNCEMENT.toLog4j() <= WARNING.toLog4j()) shouldBe true
        }
    }
}

/**
 * Records logging events produced by Log4J loggers.
 */
private class Log4jRecorder(loggerName: String, minLevel: Level): Recorder(minLevel) {

    private val logger: Logger = (LogManager.getLogger(loggerName) as Logger)
    private var prevRootLevel: L4jLevel? = null
    private var prevLevel: L4jLevel? = null
    private var appender: Appender? = null

    override fun start() {
        logger.apply {
            prevLevel = level
            level = minLevel.toLog4j()
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
        rootLogger.level = INFO.toLog4j()
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

private fun Level.toLog4j(): L4jLevel {
    return when (this) {
        OFF -> L4jLevel.OFF
        ERROR -> L4jLevel.ERROR
        WARNING -> L4jLevel.WARN
        INFO -> L4jLevel.INFO
        DEBUG -> L4jLevel.DEBUG
        TRACE -> L4jLevel.TRACE
        ALL -> L4jLevel.ALL
        else -> L4jLevel.forName(this.name, this.value.scaleToLog4j())
    }
}

/**
 * Transforms the level of standard logging in Java (JUL) scale to
 * a value in Log4J scale.
 *
 * In the terms of log records volume, JUL operates in the range of
 * `[OFF(Int.MAX_VALUE], ALL(Int.MIN_VALUE)]`, where `OFF` means no logging, and
 * `ALL` means all logging records will be made.
 *
 * The LogJ scale in these terms operate in the range `[OFF(0), ALL(Int.MAX_VALUE)]`.
 *
 * This method transforms JUL level value into Log4J level value for the cases of
 * non-standard levels. The standard levels are transformed using their exact values.
 *
 * @see toLog4j
 */
private fun Int.scaleToLog4j(): Int {
    val minLevel = OFF.value
    val maxLevel = ALL.value
    val julLength = (maxLevel.toDouble() - minLevel).absoluteValue
    val log4JLength = L4jLevel.ALL.intLevel() - L4jLevel.OFF.intLevel()
    // Convert the current value as if `minLevel` were zero and `maxLevel` were 100.
    val zeroed: Double = ((this.toDouble() - minLevel).absoluteValue / julLength) * 100
    // Convert to the value in the range [OFF(0), ALL(Int.MAX_VALUE)] used by Log4J,
    // adjusting the value by the ration of the range lengths.
    val converted: Double = zeroed * (log4JLength / julLength)
    return converted.toInt()
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
