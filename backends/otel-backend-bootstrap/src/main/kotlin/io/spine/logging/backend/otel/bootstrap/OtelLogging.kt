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
import io.opentelemetry.kotlin.export.TelemetryCloseable
import io.opentelemetry.kotlin.logging.export.batchLogRecordProcessor
import io.opentelemetry.kotlin.logging.export.otlpHttpLogRecordExporter
import io.spine.logging.backend.otel.OtelBackendSettings
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
     */
    public const val DEFAULT_OTLP_HTTP_ENDPOINT: String = "http://localhost:4318"

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
            runBlocking { (openTelemetry as? TelemetryCloseable)?.shutdown() }
        }
    }

    /**
     * Installs an OTLP/HTTP pipeline using the endpoint from the
     * `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable, falling back to
     * [DEFAULT_OTLP_HTTP_ENDPOINT] when it is not set.
     */
    public fun fromEnvironment(): AutoCloseable {
        val endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
            ?: DEFAULT_OTLP_HTTP_ENDPOINT
        return installOtlpHttp(endpoint)
    }
}
