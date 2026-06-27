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
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.spine.logging.Level

/**
 * Maps a Spine logging [Level] onto an OpenTelemetry [SeverityNumber].
 *
 * The mapping is by numeric threshold rather than by name, so that custom
 * levels (and the standard aliases `SEVERE`, `FINE`, `TRACE`) degrade to the
 * nearest standard severity instead of falling through to a default.
 *
 * `CONFIG` (700) has no exact OpenTelemetry counterpart — it sits between
 * `INFO` and `DEBUG` in `java.util.logging` semantics — and is mapped to
 * [SeverityNumber.DEBUG4], the most detailed `DEBUG` band, keeping it just
 * above plain `DEBUG`.
 */
internal fun Level.toSeverityNumber(): SeverityNumber = when {
    value >= Level.FATAL.value -> SeverityNumber.FATAL     // 2000 -> 21
    value >= Level.ERROR.value -> SeverityNumber.ERROR     // 1000 -> 17
    value >= Level.WARNING.value -> SeverityNumber.WARN    //  900 -> 13
    value >= Level.INFO.value -> SeverityNumber.INFO       //  800 ->  9
    value >= Level.CONFIG.value -> SeverityNumber.DEBUG4   //  700 ->  8
    value >= Level.DEBUG.value -> SeverityNumber.DEBUG     //  500 ->  5
    value >= Level.FINER.value -> SeverityNumber.TRACE2    //  400 ->  2
    else -> SeverityNumber.TRACE                           //  300 ->  1
}
