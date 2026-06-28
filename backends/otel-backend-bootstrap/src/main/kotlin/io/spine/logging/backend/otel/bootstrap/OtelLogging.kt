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
     * The environment variable that overrides [DEFAULT_OTLP_HTTP_ENDPOINT].
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
            // The SDK instance created by `createOpenTelemetry` is a `TelemetryCloseable`;
            // the safe cast future-proofs against the factory ever returning otherwise.
            OtelBackendSettings.use(NoopOpenTelemetry)
            val result = runBlocking { (openTelemetry as? TelemetryCloseable)?.shutdown() }
            if (result == OperationResultCode.Failure) {
                // A failed flush/shutdown means buffered records may be lost; do not let
                // it pass silently. Report via JUL: the Spine OTel backend has just been
                // pointed at the no-op instance, so logging through it here would itself
                // be dropped.
                Logger.getLogger(OtelLogging::class.java.name).warning(
                    "The OpenTelemetry SDK reported a failure while shutting down; " +
                        "some buffered log records may not have been exported."
                )
            }
        }
    }

    /**
     * Installs an OTLP/HTTP pipeline using the endpoint from the
     * `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable, falling back to
     * [DEFAULT_OTLP_HTTP_ENDPOINT] when it is not set.
     */
    public fun fromEnvironment(): AutoCloseable =
        installOtlpHttp(endpointFromEnvironment())

    /**
     * Resolves the OTLP/HTTP endpoint from the `OTEL_EXPORTER_OTLP_ENDPOINT`
     * environment variable, falling back to [DEFAULT_OTLP_HTTP_ENDPOINT] when it is
     * not set.
     *
     * Exposed as `internal` so the resolution can be verified without building an
     * exporter or touching the network.
     */
    internal fun endpointFromEnvironment(): String =
        System.getenv(OTLP_ENDPOINT_ENV) ?: DEFAULT_OTLP_HTTP_ENDPOINT
}
