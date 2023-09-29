/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.logging.flogger.given

import io.spine.logging.flogger.AbstractLogger
import io.spine.logging.flogger.FloggerApi
import io.spine.logging.flogger.LogContext
import io.spine.logging.flogger.backend.LoggerBackend
import io.spine.logging.flogger.parser.DefaultPrintfMessageParser
import io.spine.logging.flogger.parser.MessageParser
import java.util.logging.Level

/**
 * Helper class for testing backend and context implementations.
 *
 * Unlike normal logger instances, this one can be reconfigured dynamically.
 * It has specific methods for injecting timestamps and forcing log statements.
 *
 * @see <a href="https://rb.gy/smalv">Original Java code of Google Flogger</a>
 */
class ConfigurableLogger(backend: LoggerBackend) : AbstractLogger<ConfigurableLogger.Api>(backend) {

    companion object {
        // Midnight Jan 1st, 2000 (GMT)
        private const val DEFAULT_TIMESTAMP_NANOS = 946684800000000000L
    }

    /**
     * Non-wildcard logging API for the [ConfigurableLogger].
     */
    interface Api : FloggerApi<Api>

    /**
     * Logs at the given level with the fixed [DEFAULT_TIMESTAMP_NANOS].
     */
    override fun at(level: Level): Api {
        return at(level, DEFAULT_TIMESTAMP_NANOS)
    }

    /**
     * Logs at the given level with the specified nanosecond timestamp.
     */
    fun at(level: Level, timestampNanos: Long): Api {
        return Context(level, false, timestampNanos)
    }

    /**
     * Forces logging at the given level with the specified nanosecond timestamp.
     */
    fun forceAt(level: Level, timestampNanos: Long = DEFAULT_TIMESTAMP_NANOS): Api {
        return Context(level, true, timestampNanos)
    }

    /**
     * Context that implements the logger's [Api].
     */
    private inner class Context(level: Level, isForced: Boolean, timestampNanos: Long) :
        LogContext<ConfigurableLogger, Api>(level, isForced, timestampNanos), Api {

        override fun getLogger(): ConfigurableLogger = this@ConfigurableLogger

        override fun api(): Api  = this

        override fun noOp(): Api = throw UnsupportedOperationException()

        override fun getMessageParser(): MessageParser = DefaultPrintfMessageParser.getInstance()
    }
}

