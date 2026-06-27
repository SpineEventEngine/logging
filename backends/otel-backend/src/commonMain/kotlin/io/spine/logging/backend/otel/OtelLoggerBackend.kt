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

@file:OptIn(ExperimentalApi::class)

package io.spine.logging.backend.otel

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.spine.logging.Level
import io.spine.logging.LogContext
import io.spine.logging.backend.LogData
import io.spine.logging.backend.LoggerBackend
import io.spine.logging.backend.MetadataProcessor
import io.spine.logging.backend.Platform
import io.spine.logging.backend.SimpleMessageFormatter

/**
 * A [LoggerBackend] that emits Spine log statements as OpenTelemetry log records.
 *
 * Each [LogData] is mapped onto a single [Logger.emit] call:
 *
 * - the rendered message (without the `[CONTEXT …]` suffix) becomes the body;
 * - the [Level] becomes the [SeverityNumber] (see [toSeverityNumber]) and the
 *   severity text;
 * - the throwable carried by [LogContext.Key.LOG_CAUSE] becomes the exception;
 * - the timestamp is passed through as epoch nanoseconds;
 * - the log site and the merged scope/log-site metadata become attributes
 *   (see [putLogSite] and [putMetadata]).
 *
 * The active OpenTelemetry [Context][io.opentelemetry.kotlin.context.Context] is
 * left implicit (`context = null`), so the SDK stamps the current span's
 * trace and span identifiers onto the record.
 *
 * @param logger The OpenTelemetry logger to emit records with.
 * @param loggerName The instrumentation scope name, used by the SPI.
 */
internal class OtelLoggerBackend(
    private val logger: Logger,
    override val loggerName: String?,
) : LoggerBackend() {

    override fun isLoggable(level: Level): Boolean =
        logger.enabled(severityNumber = level.toSeverityNumber())

    override fun log(data: LogData) {
        val metadata = MetadataProcessor.forScopeAndLogSite(
            Platform.getInjectedMetadata(),
            data.metadata
        )
        val cause = metadata.getSingleValue(LogContext.Key.LOG_CAUSE)
        logger.emit(
            body = SimpleMessageFormatter.getLiteralLogMessage(data),
            timestamp = data.timestampNanos,
            severityNumber = data.level.toSeverityNumber(),
            severityText = data.level.name,
            exception = cause,
            attributes = {
                putLogSite(data)
                putMetadata(metadata)
            }
        )
    }

    override fun handleError(error: RuntimeException, badData: LogData) {
        logger.emit(
            body = "Spine logging backend error: ${error.message}",
            severityNumber = SeverityNumber.ERROR,
            severityText = SeverityNumber.ERROR.name,
            exception = error,
            attributes = {
                setStringAttribute("spine.logging.bad_data", badData.toString())
            }
        )
    }
}
