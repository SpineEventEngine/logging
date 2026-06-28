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

import com.google.auto.service.AutoService
import io.opentelemetry.kotlin.ExperimentalApi
import io.spine.logging.backend.BackendFactory
import io.spine.logging.backend.LoggerBackend

/**
 * Creates [OtelLoggerBackend] instances backed by the [OpenTelemetry]
 * [io.opentelemetry.kotlin.logging.Logger] resolved from [OtelBackendSettings].
 *
 * When using `io.spine.logging.backend.system.DefaultPlatform`, this factory is
 * picked up automatically via `ServiceLoader` (registered by `@AutoService`) if
 * it is the only [BackendFactory] on the classpath. To select it explicitly when
 * several backends are present, set the `spine.logging.backend_factory` system
 * property to this class' fully-qualified name.
 *
 * The instrumentation scope name passed to the logger provider is the logging class
 * name converted by the shared `loggerName` convention of [BackendFactory] (`$`
 * replaced by `.`), matching the other Spine Logging backends.
 */
@AutoService(BackendFactory::class)
public class OtelBackendFactory : BackendFactory() {

    override fun create(loggingClass: String): LoggerBackend {
        val name = loggerName(loggingClass)
        return OtelLoggerBackend(name)
    }

    /** Returns a fully-qualified name of this class. */
    override fun toString(): String = javaClass.name
}
