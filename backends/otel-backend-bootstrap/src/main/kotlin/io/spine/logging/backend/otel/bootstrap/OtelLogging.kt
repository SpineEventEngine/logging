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

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.export.TelemetryCloseable
import io.opentelemetry.kotlin.logging.export.batchLogRecordProcessor
import io.opentelemetry.kotlin.logging.export.otlpHttpLogRecordExporter
import io.spine.logging.backend.otel.OtelBackendSettings
import java.util.logging.Logger
import kotlinx.coroutines.runBlocking

/**
 * Turnkey wiring of the OpenTelemetry logging backend.
 *
 * The core backend stays API-only and expects an `OpenTelemetry` instance to be
 * injected via [OtelBackendSettings]. This optional module builds a real native
 * Kotlin SDK with an OTLP/HTTP export pipeline and installs it.
 *
 * The returned [AutoCloseable] uninstalls the backend (restoring the no-op
 * instance) and shuts down the SDK it created when closed.
 */
public object OtelLogging {

    /**
     * The default OTLP/HTTP endpoint of a local OpenTelemetry Collector.
     *
     * Port `4318` is the OpenTelemetry-standard OTLP/HTTP port, so this default
     * matches a collector started with stock settings. When your collector listens
     * elsewhere, override it: pass an explicit endpoint to [installOtlpHttp], or set
     * the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable and use [fromEnvironment].
     */
    public const val DEFAULT_OTLP_HTTP_ENDPOINT: String = "http://localhost:4318"

    /**
     * The signal-specific environment variable that overrides
     * [DEFAULT_OTLP_HTTP_ENDPOINT], taking precedence over [OTLP_ENDPOINT_ENV].
     */
    private const val OTLP_LOGS_ENDPOINT_ENV: String = "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT"

    /**
     * The generic OTLP environment variable that overrides [DEFAULT_OTLP_HTTP_ENDPOINT]
     * when [OTLP_LOGS_ENDPOINT_ENV] is not set.
     */
    private const val OTLP_ENDPOINT_ENV: String = "OTEL_EXPORTER_OTLP_ENDPOINT"

    /**
     * Builds a native Kotlin OpenTelemetry SDK that exports log records over
     * OTLP/HTTP to [endpoint] and installs it into [OtelBackendSettings].
     *
     * @param endpoint The OTLP/HTTP endpoint. Defaults to [DEFAULT_OTLP_HTTP_ENDPOINT].
     * @return A handle that uninstalls the backend when closed.
     */
    public fun installOtlpHttp(endpoint: String = DEFAULT_OTLP_HTTP_ENDPOINT): AutoCloseable {
        val openTelemetry = createOpenTelemetry {
            loggerProvider {
                export {
                    batchLogRecordProcessor(
                        otlpHttpLogRecordExporter(endpoint)
                    )
                }
            }
        }
        OtelBackendSettings.use(openTelemetry)
        return AutoCloseable {
            // Stop routing new records to this SDK, then flush and shut it down.
            OtelBackendSettings.use(NoopOpenTelemetry)
            val result =
                if (openTelemetry is TelemetryCloseable) runBlocking { openTelemetry.shutdown() }
                else null
            reportShutdown(openTelemetry, result)
        }
    }

    /**
     * Warns, via JUL, when the SDK could not be shut down cleanly: it is not a
     * [TelemetryCloseable] (so it was never shut down), or its `shutdown()` reported a
     * failure. Either way buffered records may be lost, so the outcome must not pass
     * silently — JUL is used because the Spine OTel backend has just been pointed at the
     * no-op instance, so logging through it here would itself be dropped.
     *
     * Exposed as `internal` (with an injectable [logger]) so both outcomes can be verified
     * without provoking a real SDK failure.
     */
    internal fun reportShutdown(
        sdk: Any,
        result: OperationResultCode?,
        logger: Logger = Logger.getLogger(OtelLogging::class.java.name),
    ) {
        val warning = when {
            sdk !is TelemetryCloseable ->
                "The OpenTelemetry SDK is not a TelemetryCloseable; it was not shut down, " +
                    "so buffered log records may not have been exported."
            result == OperationResultCode.Failure ->
                "The OpenTelemetry SDK reported a failure while shutting down; " +
                    "some buffered log records may not have been exported."
            else -> return
        }
        logger.warning(warning)
    }

    /**
     * Installs an OTLP/HTTP pipeline using the endpoint resolved from the environment.
     *
     * The signal-specific `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` takes precedence over the
     * generic `OTEL_EXPORTER_OTLP_ENDPOINT`, falling back to [DEFAULT_OTLP_HTTP_ENDPOINT]
     * when neither is set.
     */
    public fun fromEnvironment(): AutoCloseable =
        installOtlpHttp(endpointFromEnvironment())

    /**
     * Resolves the OTLP/HTTP base endpoint from the environment, preferring the
     * signal-specific [OTLP_LOGS_ENDPOINT_ENV] over the generic [OTLP_ENDPOINT_ENV] and
     * falling back to [DEFAULT_OTLP_HTTP_ENDPOINT] when neither is set.
     *
     * The value is treated as a base URL: the exporter appends the OTLP/HTTP logs path
     * (`/v1/logs`) to it, just as it does for the default.
     *
     * Exposed as `internal` so the resolution can be verified without touching the real
     * environment or the network.
     */
    internal fun endpointFromEnvironment(): String =
        endpointFromEnvironment { System.getenv(it) }

    /**
     * Resolves the endpoint as [endpointFromEnvironment], looking variables up through
     * [getenv]. Separated so the precedence can be tested without mutating the process
     * environment.
     */
    internal fun endpointFromEnvironment(getenv: (String) -> String?): String =
        getenv(OTLP_LOGS_ENDPOINT_ENV) ?: getenv(OTLP_ENDPOINT_ENV) ?: DEFAULT_OTLP_HTTP_ENDPOINT
}
