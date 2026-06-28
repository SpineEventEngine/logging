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

package io.spine.logging.backend.otel.bootstrap

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.export.TelemetryCloseable
import java.net.ServerSocket
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`OtelLogging` should")
internal class OtelLoggingSpec {

    @Test
    fun `resolve the default endpoint when the environment variable is unset`() {
        // `OTEL_EXPORTER_OTLP_ENDPOINT` is not set in the test JVM, so resolution
        // falls back to the default. This is pure logic: no exporter is built and no
        // port is touched.
        OtelLogging.endpointFromEnvironment() shouldBe OtelLogging.DEFAULT_OTLP_HTTP_ENDPOINT
    }

    @Test
    fun `prefer the logs-specific endpoint over the generic one`() {
        val resolved = OtelLogging.endpointFromEnvironment {
            when (it) {
                "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT" -> "http://logs:4318"
                "OTEL_EXPORTER_OTLP_ENDPOINT" -> "http://generic:4318"
                else -> null
            }
        }
        resolved shouldBe "http://logs:4318"
    }

    @Test
    fun `use the generic endpoint when no logs-specific one is set`() {
        val resolved = OtelLogging.endpointFromEnvironment {
            if (it == "OTEL_EXPORTER_OTLP_ENDPOINT") "http://generic:4318" else null
        }
        resolved shouldBe "http://generic:4318"
    }

    @Test
    fun `fall back to the default when no endpoint variable is set`() {
        OtelLogging.endpointFromEnvironment { null } shouldBe OtelLogging.DEFAULT_OTLP_HTTP_ENDPOINT
    }

    @Test
    fun `build, install and shut down an OTLP HTTP pipeline`() {
        shouldNotThrowAny {
            // Aim the exporter at a free local port rather than the well-known 4318:
            // the test must not collide with — or accidentally export into — a real
            // collector or a parallel suite on the standard port. The batch processor
            // exports asynchronously, so construction, installation and shutdown all
            // succeed whether or not anything is listening.
            val installed = OtelLogging.installOtlpHttp("http://localhost:${freePort()}")
            installed.close()
        }
    }

    @Test
    fun `install via the no-arg overload and from the environment`() {
        shouldNotThrowAny {
            // Both resolve to the default endpoint. Nothing is listening and no records are
            // emitted, so this exercises the wiring without exporting anything.
            OtelLogging.installOtlpHttp().close()
            OtelLogging.fromEnvironment().close()
        }
    }

    @Test
    fun `warn when the SDK is not closeable`() {
        val (logger, records) = capturingLogger()
        OtelLogging.reportShutdown(sdk = Any(), result = null, logger = logger)
        records shouldHaveSize 1
        records.first().level shouldBe Level.WARNING
        records.first().message shouldContain "not a TelemetryCloseable"
    }

    @Test
    fun `warn when shutdown reports a failure`() {
        val (logger, records) = capturingLogger()
        OtelLogging.reportShutdown(closeableSdk, OperationResultCode.Failure, logger)
        records shouldHaveSize 1
        records.first().message shouldContain "reported a failure"
    }

    @Test
    fun `stay silent on a successful shutdown`() {
        val (logger, records) = capturingLogger()
        OtelLogging.reportShutdown(closeableSdk, OperationResultCode.Success, logger)
        records shouldHaveSize 0
    }

    /** Returns a currently-free local TCP port. */
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** A logger that records what it publishes, with parent handlers disabled. */
    private fun capturingLogger(): Pair<Logger, List<LogRecord>> {
        val records = mutableListOf<LogRecord>()
        val logger = Logger.getAnonymousLogger().apply {
            useParentHandlers = false
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) { records += record }
                override fun flush() = Unit
                override fun close() = Unit
            })
        }
        return logger to records
    }

    private companion object {
        /** A minimal `TelemetryCloseable` SDK stub for shutdown-reporting tests. */
        private val closeableSdk = object : TelemetryCloseable {
            override suspend fun forceFlush() = OperationResultCode.Success
            override suspend fun shutdown() = OperationResultCode.Success
        }
    }
}
