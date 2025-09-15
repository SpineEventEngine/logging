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

@file:JvmName("LogEvents")

package io.spine.logging.backend.log4j2

import io.spine.logging.KeyValueHandler
import io.spine.logging.Level
import io.spine.logging.LogContext
import io.spine.logging.MetadataKey
import io.spine.logging.backend.LogData
import io.spine.logging.backend.MetadataHandler
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
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.impl.ContextDataFactory
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.apache.logging.log4j.core.time.Instant
import org.apache.logging.log4j.core.time.MutableInstant
import org.apache.logging.log4j.message.SimpleMessage
import org.apache.logging.log4j.util.StringMap
import org.apache.logging.log4j.Level as L4jLevel

/**
 * Helper to format [LogData].
 */
public fun toLog4jLogEvent(loggerName: String, logData: LogData): LogEvent {
    val metadata = io.spine.logging.backend.MetadataProcessor.forScopeAndLogSite(
        Platform.getInjectedMetadata(), logData.metadata
    )

    // See JavaDoc in the original version for details about DefaultConfiguration handling.
    val ctx = LoggerContext.getContext(false)
    val config = ctx.configuration
    val message: String = if (config is DefaultConfiguration) {
        SimpleMessageFormatter.getDefaultFormatter().format(logData, metadata)
    } else {
        error("Unable to format a message for the configuration: `$config`.")
    }

    val thrown = metadata.getSingleValue(LogContext.Key.LOG_CAUSE)
    return toLog4jLogEvent(
        loggerName, logData, message, logData.level.toLog4j(), thrown
    )
}

/**
 * Helper to format [LogData].
 */
public fun toLog4jLogEvent(
    loggerName: String,
    error: RuntimeException,
    badData: LogData
): LogEvent {
    val message = formatBadLogData(error, badData)
    val level =
        if (badData.level.value < WARNING.intValue()) WARNING.toLevel() else badData.level
    return toLog4jLogEvent(loggerName, badData, message, level.toLog4j(), error)
}

private fun toLog4jLogEvent(
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
    val metadataProcessor = io.spine.logging.backend.MetadataProcessor.forScopeAndLogSite(
        Platform.getInjectedMetadata(), logData.metadata
    )

    val contextData = ContextDataFactory.createContextData(metadataProcessor.keyCount())
    val kvh = KeyValueHandler { key, value ->
        requireNonNull(value)
        contextData.putValue(
            key,
            ValueQueue.maybeWrap(value, contextData.getValue(key))
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
