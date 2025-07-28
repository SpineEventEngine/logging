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

package io.spine.logging.backend.log4j2.given

import io.spine.logging.jvm.AbstractLogger
import io.spine.logging.jvm.MiddlemanApi
import io.spine.logging.jvm.LogContext
import io.spine.logging.jvm.backend.LoggerBackend
import io.spine.logging.jvm.parser.DefaultPrintfMessageParser
import io.spine.logging.jvm.parser.MessageParser
import java.util.logging.Level

/**
 * Dynamically reconfigurable logger for testing backend
 * and context implementations.
 *
 * This logger has specific methods for injecting timestamps
 * and forcing log statements.
 *
 * @see <a href="https://rb.gy/smalv">
 *     Original Java code of Google Flogger</a> for historical context.
 */
internal class StubLogger(backend: LoggerBackend) : AbstractLogger<StubLogger.Api>(backend) {

    companion object {
        // Midnight Jan 1st, 2000 (GMT)
        private const val DEFAULT_TIMESTAMP_NANOS = 946684800000000000L
    }

    /**
     * Non-wildcard logging API for the [StubLogger].
     */
    interface Api : MiddlemanApi<Api>

    /**
     * Logs at the given level with the fixed [DEFAULT_TIMESTAMP_NANOS].
     */
    override fun at(level: Level): Api =
        at(level, DEFAULT_TIMESTAMP_NANOS)

    /**
     * Logs at the given level with the specified nanosecond timestamp.
     */
    fun at(level: Level, timestampNanos: Long): Api =
        Context(level, false, timestampNanos)

    /**
     * Context that implements the logger's [Api].
     */
    private inner class Context(level: Level, isForced: Boolean, timestampNanos: Long) :
        LogContext<StubLogger, Api>(level, isForced, timestampNanos), Api {

        override fun getLogger(): StubLogger = this@StubLogger

        override fun api(): Api  = this

        override fun noOp(): Api = throw UnsupportedOperationException()

        override fun getMessageParser(): MessageParser = DefaultPrintfMessageParser.getInstance()
    }
}
