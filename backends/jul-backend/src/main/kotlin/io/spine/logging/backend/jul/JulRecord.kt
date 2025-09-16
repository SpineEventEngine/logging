/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.backend.jul

import io.spine.logging.LogContext
import io.spine.logging.backend.LogData
import io.spine.logging.backend.Metadata
import java.io.Serial

/**
 * An eagerly evaluating JUL [java.util.logging.LogRecord] that can be passed to a normal handler.
 */
public class JulRecord : AbstractJulRecord {

    public companion object {

        @Serial
        private const val serialVersionUID: Long = 0L

        /** Creates a [JulRecord] for a normal log statement from the given data. */
        @JvmStatic
        public fun create(data: LogData, scope: Metadata): JulRecord = JulRecord(data, scope)

        /**
         * Deprecated. Use [create] and pass scoped metadata in.
         */
        @Deprecated("Use create(LogData, Metadata)")
        @JvmStatic
        public fun create(data: LogData): JulRecord = create(data, Metadata.empty())

        /** Creates a [JulRecord] in the case of an error during logging. */
        @JvmStatic
        public fun error(error: RuntimeException, data: LogData, scope: Metadata): JulRecord =
            JulRecord(error, data, scope)

        /**
         * Deprecated. Use [error] and pass scoped metadata in.
         */
        @Deprecated("Use error(RuntimeException, LogData, Metadata)")
        @JvmStatic
        public fun error(error: RuntimeException, data: LogData): JulRecord =
            error(error, data, Metadata.empty())
    }

    private constructor(data: LogData, scope: Metadata): super(data, scope) {
        thrown = getMetadataProcessor().getSingleValue(LogContext.Key.LOG_CAUSE)
        // Force message formatting early to avoid deadlocks in some JUL handlers.
        @Suppress("UNUSED_VARIABLE", "unused")
        val unused = message
    }

    private constructor(error: RuntimeException, data: LogData, scope: Metadata) : super(
        error,
        data,
        scope
    )
}
