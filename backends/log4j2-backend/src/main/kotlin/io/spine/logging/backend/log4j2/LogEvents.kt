/*
 * Copyright 2026, TeamDev. All rights reserved.
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

@file:JvmName("LogEvents")

package io.spine.logging.backend.log4j2

import io.spine.logging.KeyValueHandler
import io.spine.logging.Level
import io.spine.logging.LogContext
import io.spine.logging.MetadataKey
import io.spine.logging.backend.LogData
import io.spine.logging.backend.MetadataHandler
import io.spine.logging.backend.MetadataProcessor
import io.spine.logging.backend.Platform
import io.spine.logging.backend.SimpleMessageFormatter
import io.spine.logging.context.ScopedLoggingContext
import io.spine.logging.context.Tags
import io.spine.logging.toLevel
import java.util.Objects.requireNonNull
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.logging.Level.FINE
import java.util.logging.Level.INFO
import java.util.logging.Level.SEVERE
import java.util.logging.Level.WARNING
import java.util.stream.Collectors
import java.util.stream.StreamSupport
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.impl.ContextDataFactory
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.apache.logging.log4j.core.time.Instant
import org.apache.logging.log4j.core.time.MutableInstant
import org.apache.logging.log4j.message.SimpleMessage
import org.apache.logging.log4j.util.StringMap
import org.apache.logging.log4j.Level as L4jLevel

/**
 * Converts this [LogData] to a Log4j2 [LogEvent] to be logged by the given [logger].
 *
 * The way the log message is formatted depends on the configuration of
 * the [logger]'s context — the configuration whose layouts render the event.
 *
 * If no configuration file was located, Log4j2 falls back to
 * [DefaultConfiguration], whose hard-wired console layout ignores the context
 * data of a log event. In this case, the metadata is appended to the message
 * itself — in the `[CONTEXT key="value" ... ]` form — so that it is not lost.
 *
 * With a user-provided configuration, the layout is under the user's control,
 * and only the log message itself becomes the Log4j2 message. The metadata is
 * carried by the context data map of the created event, where a pattern layout
 * can render it, e.g., via `%X`.
 */
internal fun LogData.toLog4jEvent(logger: Logger): LogEvent {
    val metadata = MetadataProcessor.forScopeAndLogSite(
        Platform.getInjectedMetadata(), this.metadata
    )

    /*
     * The type of the configuration tells whether a configuration file was
     * loaded (or the default configuration was overwritten by other means,
     * such as a custom configuration factory).
     *
     * Unlike the original Flogger implementation, which inspects the statically
     * looked-up `LoggerContext.getContext(false)`, the check deliberately uses
     * the context of the logger rendering the event. The two may differ in
     * multi-context deployments (per-webapp or OSGi context selectors, etc.),
     * and only the rendering context determines whether context data is shown.
     *
     * Be aware that `LoggerContext` and `DefaultConfiguration` are not a part
     * of the public Log4j2 API, and this behavior can change with any minor release.
     */
    val config = logger.context.configuration
    val message: String = if (config is DefaultConfiguration) {
        SimpleMessageFormatter.getDefaultFormatter().format(this, metadata)
    } else {
        SimpleMessageFormatter.getLiteralLogMessage(this)
    }

    val thrown = metadata.getSingleValue(LogContext.Key.LOG_CAUSE)
    return toLog4jEvent(
        loggerName = logger.name,
        logData = this,
        message = message,
        level = level.toLog4j(),
        thrown = thrown
    )
}

/**
 * Converts this erroneous [LogData] to a Log4j2 [LogEvent] describing
 * the given [error], to be logged by the given [logger].
 */
internal fun LogData.toLog4jEvent(logger: Logger, error: RuntimeException): LogEvent {
    val message = formatBadLogData(error, this)
    // Re-target this log message as a warning (or above) since it indicates a real bug.
    val level =
        if (this.level.value < WARNING.intValue()) WARNING.toLevel() else this.level
    return toLog4jEvent(
        loggerName = logger.name,
        logData = this,
        message = message,
        level = level.toLog4j(),
        thrown = error
    )
}

private fun toLog4jEvent(
    loggerName: String,
    logData: LogData,
    message: String,
    level: L4jLevel,
    thrown: Throwable?
): LogEvent {
    val logSite = logData.logSite
    val locationInfo = StackTraceElement(
        logSite.className,
        logSite.methodName,
        logSite.fileName,
        logSite.lineNumber
    )

    return Log4jLogEvent.newBuilder()
        .setLoggerName(loggerName)
        .setLoggerFqcn(logData.loggerName)
        .setLevel(level)
        .setMessage(SimpleMessage(message))
        .setThreadName(Thread.currentThread().name)
        .setInstant(getInstant(logData.timestampNanos))
        .setThrown(thrown)
        .setIncludeLocation(true)
        .setSource(locationInfo)
        .setContextData(createContextMap(logData))
        .build()
}

@Suppress("NAME_SHADOWING")
private fun getInstant(timestampNanos: Long): Instant {
    val instant = MutableInstant()
    val epochSeconds = NANOSECONDS.toSeconds(timestampNanos)
    val remainingNanos = (timestampNanos - SECONDS.toNanos(epochSeconds)).toInt()
    instant.initFromEpochSecond(epochSeconds, remainingNanos)
    return instant
}

@Suppress("TooGenericExceptionCaught")
private fun formatBadLogData(error: RuntimeException, badLogData: LogData): String {
    val errorMsg = StringBuilder("LOGGING ERROR: ").append(error.message).append('\n')
    val length = errorMsg.length
    return try {
        appendLogData(badLogData, errorMsg)
        errorMsg.toString()
    } catch (e: RuntimeException) {
        errorMsg.setLength(length)
        errorMsg.append("Cannot append LogData: ").append(e)
        errorMsg.toString()
    }
}

/** Appends the given [LogData] to the given [StringBuilder]. */
@Suppress("HardcodedLineSeparator")
private fun appendLogData(data: LogData, out: StringBuilder) {
    out.append("  original message: ")
    out.append(data.literalArgument)
    val metadata = data.metadata
    if (metadata.size() > 0) {
        out.append("\n  metadata:")
        for (n in 0 until metadata.size()) {
            out.append("\n    ")
            out.append(metadata.getKey(n).label)
                .append(": ")
                .append(metadata.getValue(n))
        }
    }
    out.append("\n  level: ").append(data.level)
    out.append("\n  timestamp (nanos): ").append(data.timestampNanos)
    out.append("\n  class: ").append(data.logSite.className)
    out.append("\n  method: ").append(data.logSite.methodName)
    out.append("\n  line number: ").append(data.logSite.lineNumber)
}

private val HANDLER: MetadataHandler<KeyValueHandler> =
    MetadataHandler.builder<KeyValueHandler> { key, value, kvh ->
        handleMetadata(key, value, kvh)
    }.build()

private fun handleMetadata(key: MetadataKey<Any>, value: Any, kvh: KeyValueHandler) {
    if (key.javaClass == LogContext.Key.TAGS.javaClass) {
        processTags(key, value, kvh)
    } else {
        if (value is Tags) {
            processTags(key, value, kvh)
        } else {
            ValueQueue.appendValues(key.label, value, kvh)
        }
    }
}

private fun processTags(key: MetadataKey<Any>, value: Any, kvh: KeyValueHandler) {
    val valueQueue = ValueQueue.appendValueToNewQueue(value)
    ValueQueue.appendValues(
        key.label,
        if (valueQueue.size() == 1) StreamSupport.stream(valueQueue.spliterator(), false)
            .collect(Collectors.toList()) else valueQueue,
        kvh
    )
}

/**
 * We do not support MDC/NDC merging. Use [ScopedLoggingContext].
 */
private fun createContextMap(logData: LogData): StringMap {
    val metadataProcessor = MetadataProcessor.forScopeAndLogSite(
        Platform.getInjectedMetadata(), logData.metadata
    )

    val contextData = ContextDataFactory.createContextData(metadataProcessor.keyCount())
    val kvh = KeyValueHandler { key, value ->
        requireNonNull(value)
        contextData.putValue(
            key,
            ValueQueue.maybeWrap(value!!, contextData.getValue(key))
        )
    }
    metadataProcessor.process(HANDLER, kvh)
    contextData.freeze()
    return contextData
}

/**
 * Converts this [java.util.logging.Level] to [org.apache.logging.log4j.Level].
 */
public fun Level.toLog4j(): L4jLevel {
    return when {
        value < FINE.intValue() -> L4jLevel.TRACE
        value < INFO.intValue() -> L4jLevel.DEBUG
        value < WARNING.intValue() -> L4jLevel.INFO
        value < SEVERE.intValue() -> L4jLevel.WARN
        else -> L4jLevel.ERROR
    }
}
