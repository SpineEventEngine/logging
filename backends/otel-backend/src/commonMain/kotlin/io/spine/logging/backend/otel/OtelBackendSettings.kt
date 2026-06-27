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
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry

/**
 * Holds the [OpenTelemetry] instance the backend emits log records with.
 *
 * The Spine backend SPI constructs a [OtelBackendFactory] without arguments
 * (via `ServiceLoader` on the JVM), so the instance cannot be supplied through
 * the factory constructor. Instead, the application injects it here once, at
 * startup, before any logging happens:
 *
 * ```
 * OtelBackendSettings.use(createOpenTelemetry { loggerProvider { export { … } } })
 * ```
 *
 * Until [use] is called, the backend emits to [NoopOpenTelemetry] and records
 * are dropped. This is a new convention for Spine backends — no other shipped
 * backend supports programmatic injection — and it mirrors the lazily-resolved,
 * default-then-override shape of the platform's own backend-factory lookup.
 */
public object OtelBackendSettings {

    @Volatile
    private var instance: OpenTelemetry = NoopOpenTelemetry

    /**
     * Sets the [OpenTelemetry] instance the backend will use.
     *
     * Call this once during application startup. The supplied instance is owned
     * by the caller, including its lifecycle and shutdown.
     */
    public fun use(openTelemetry: OpenTelemetry) {
        instance = openTelemetry
    }

    /**
     * Returns the currently configured [OpenTelemetry] instance,
     * or [NoopOpenTelemetry] if none was injected.
     */
    internal fun current(): OpenTelemetry = instance
}
