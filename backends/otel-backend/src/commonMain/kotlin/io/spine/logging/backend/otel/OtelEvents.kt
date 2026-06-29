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

package io.spine.logging.backend.otel

import io.spine.logging.Level
import io.spine.logging.MetadataKey
import io.spine.logging.WithLogging

/**
 * The metadata key recognized by [OtelLoggerBackend] as the OpenTelemetry event name.
 *
 * When a log statement carries this key, the backend emits the record as a
 * log-based *event* — `Logger.emit(eventName = …)` — rather than a plain log
 * record, and the key itself is not duplicated as an attribute.
 *
 * This plays the role of the `otel.event.name` key recognized by the OpenTelemetry
 * Logback and Log4j appenders. The Spine metadata label must be a plain identifier
 * (letters, digits, underscore), so `otelEventName` is used; the value still becomes
 * the OpenTelemetry event name. With non-OpenTelemetry backends the key is treated
 * as ordinary metadata.
 */
public val EVENT_NAME: MetadataKey<String> = MetadataKey.single<String>("otelEventName")

/**
 * Emits a log-based OpenTelemetry event with the given [name].
 *
 * An event is an ordinary log statement that carries the [EVENT_NAME] metadata;
 * the OpenTelemetry backend maps it to `Logger.emit(eventName = name)`. Guarded by
 * the level, so a disabled event costs nothing — [message] is evaluated only when
 * the event is actually emitted.
 *
 * The [name] should be low-cardinality (for example, `acme.orders.OrderPlaced`),
 * per the OpenTelemetry event semantic conventions — dynamic values belong in
 * attributes added via [with][io.spine.logging.LoggingApi.with], not in the name.
 *
 * @param name The event name.
 * @param level The severity of the event. Defaults to [Level.INFO].
 * @param message Supplies the record body, evaluated lazily only when the event is
 *   enabled. Defaults to the [name].
 */
public fun WithLogging.logEvent(
    name: String,
    level: Level = Level.INFO,
    message: () -> String = { name },
) {
    logger.at(level)
        .with(EVENT_NAME, name)
        .log(message)
}
