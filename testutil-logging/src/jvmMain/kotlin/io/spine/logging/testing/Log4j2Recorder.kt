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

package io.spine.logging.testing/*
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

import io.spine.logging.testing.Log4jLevelConverter.backward
import io.spine.logging.testing.Log4jLevelConverter.forward
import io.spine.logging.Level
import io.spine.logging.Level.Companion.ALL
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.OFF
import io.spine.logging.Level.Companion.TRACE
import io.spine.logging.Level.Companion.WARNING
import io.spine.logging.LevelConverter
import io.spine.logging.LevelConverter.Companion.convert
import io.spine.logging.LevelConverter.Companion.reverse
import kotlin.math.absoluteValue
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.Level as L4jLevel

/**
 * Records logging events produced by Log4J loggers.
 */
public class Log4j2Recorder(loggerName: String, minLevel: Level): Recorder(minLevel) {

    private val logger: Logger = (LogManager.getLogger(loggerName) as Logger)
    private var prevRootLevel: L4jLevel? = null
    private var prevLevel: L4jLevel? = null
    private var appender: Appender? = null

    override fun start() {
        logger.apply {
            prevLevel = level
            level = minLevel.toLog4j2()
        }
        appender = object: AbstractAppender(logger.name, null, null, true, null) {
            override fun append(event: LogEvent) {
                val data = Log4jLogData(event)
                append(data)
            }
        }
        logger.addAppender(appender)

        // Set the root logger level to `INFO` to be at the same level as `java.util.logging`.
        // This is the assumption of the common tests.
        val rootLogger = LogManager.getRootLogger() as Logger
        prevRootLevel = rootLogger.level
        rootLogger.level = INFO.toLog4j2()
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

/**
 * Adapts [LogEvent] to [LogData] used by [Recorder].
 */
private class Log4jLogData(val event: LogEvent): LogData {
    override val level: Level by lazy {
        event.level.toLevel()
    }
    override val message: String = event.message.formattedMessage
    override val throwable: Throwable? = event.thrown
    override fun equals(other: Any?): Boolean {
        return if (other is Log4jLogData) {
            event == other.event
        } else {
            false
        }
    }
    override fun hashCode(): Int = event.hashCode()
}

/**
 * A reasonably high value to be used for the range calculations instead of
 * [Int.MAX_VALUE] or [Int.MIN_VALUE] used as high/low bounds in logging frameworks.
 *
 * This is a safe assumption that log levels are defined withing this range,
 * especially for tests.
 */
private const val BOUND = 1200 // java.util.logging.Level.intValue() + 1000

private val julProgression: IntProgression = BOUND.downTo(0)

private fun IntProgression.size(): Int = (last - first).absoluteValue + 1

private val log4jRange: IntRange = 0.rangeTo(BOUND)

private fun IntRange.size(): Int = (last - first + 1)

@Suppress("unused") // Is meant to force the construction of the converter object.
private val forceRegistration = Log4jLevelConverter

/**
 * Converts this logging level instance to a Log4j2 counterpart.
 *
 * This function is used in tests and thus is `internal`.
 */
public fun Level.toLog4j2(): L4jLevel = convert(this)

/**
 * Converts this Log4j2 level to Spine Logging level.
 *
 * This function is used in tests and thus is `internal`.
 */
public fun L4jLevel.toLevel(): Level = reverse(this)

private object Log4jLevelConverter: LevelConverter<L4jLevel>(
    { forward(it) },
    { backward(it) }
) {
    init {
        register(L4jLevel::class, this)
    }

    fun forward(level: Level): L4jLevel {

        /**
         * Transforms the level of standard logging in Java (JUL) scale to
         * a value in Log4J scale.
         */
        @Suppress("MagicNumber")
        fun Int.scaleToLog4j(): Int {
            val normalized =
                (toDouble() - julProgression.last).absoluteValue / julProgression.size()
            val converted = normalized * 100
            return converted.toInt()
        }

        return when (level) {
            OFF -> L4jLevel.OFF
            ERROR -> L4jLevel.ERROR
            WARNING -> L4jLevel.WARN
            INFO -> L4jLevel.INFO
            DEBUG -> L4jLevel.DEBUG
            TRACE -> L4jLevel.TRACE
            ALL -> L4jLevel.ALL
            else -> L4jLevel.forName(level.name, level.value.scaleToLog4j())
        }
    }

    fun backward(level: L4jLevel): Level {
        /**
         * Converts Log4J logging level value to JUL level value.
         */
        fun Int.scaleToJul(): Int {
            val normalized = (toDouble() - log4jRange.first) / log4jRange.size()
            val converted = (normalized * julProgression.size()) + julProgression.first
            return converted.toInt()
        }

        return when (level) {
            L4jLevel.OFF -> OFF
            L4jLevel.ERROR -> ERROR
            L4jLevel.WARN -> WARNING
            L4jLevel.INFO -> INFO
            L4jLevel.DEBUG -> DEBUG
            L4jLevel.TRACE -> TRACE
            L4jLevel.ALL -> ALL
            else -> Level(level.name(), level.intLevel().scaleToJul())
        }
    }
}
