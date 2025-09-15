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

import io.spine.annotation.VisibleForTesting
import io.spine.logging.Level
import io.spine.logging.Level.Companion.SEVERE
import io.spine.logging.backend.LoggerBackend
import io.spine.logging.toJavaLogging
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * An abstract implementation of `java.util.logging` (JUL) based backend.
 *
 * This class handles everything except formatting of a log message and metadata.
 *
 * @see <a href="https://rb.gy/jzz7x">Original Java code</a> for historical context.
 */
public abstract class AbstractJulBackend : LoggerBackend {

    private val logger: Logger

    // Internal constructor used by legacy callers - should be updated to just pass in the logging
    // class name. This needs work to handle anonymous loggers however (if that's ever supported).
    // TODO:2023-09-14:yevhenii.nadtochii: Should become `internal` when migrated to Kotlin.
    // See issue: https://github.com/SpineEventEngine/logging/issues/47
    protected constructor(logger: Logger) {
        this.logger = logger
    }

    /**
     * Constructs an abstract backend for the given class name.
     *
     * Nested or inner class names (containing '$') are converted to names matching the
     * standard JDK logger namespace by converting '$' to '.'.
     */
    protected constructor(loggingClass: String) : this(
        Logger.getLogger(
            loggingClass.replace(
                '$',
                '.'
            )
        )
    )

    public override val loggerName: String?
        get() = logger.name

    public override fun isLoggable(level: Level): Boolean = logger.isLoggable(level.toJavaLogging())

    /**
     * Logs the given record using this backend. If [wasForced] is set, the backend will make a
     * best effort attempt to bypass any log level restrictions in the underlying Java [Logger],
     * but there are circumstances in which this can fail.
     */
    public fun log(record: LogRecord, wasForced: Boolean) {
        // Do the fast boolean check (which normally succeeds) before calling isLoggable().
        if (!wasForced || logger.isLoggable(record.level)) {
            logger.log(record)
        } else {
            // In all cases we still call the filter (if one exists) even though we ignore the result.
            // Use a local variable to avoid race conditions where the filter can be unset at any time.
            val filter = logger.filter
            if (filter != null) {
                filter.isLoggable(record)
            }
            if (logger.javaClass == Logger::class.java || cannotUseForcingLogger) {
                publish(logger, record)
            } else {
                forceLoggingViaChildLogger(record)
            }
        }
    }

    private fun publish(logger: Logger, record: LogRecord) {
        for (handler in logger.handlers) {
            handler.publish(record)
        }
        if (logger.useParentHandlers) {
            val parent = logger.parent
            if (parent != null) {
                publish(parent, record)
            }
        }
    }

    // WARNING: This code will fail for anonymous loggers (getName() == null) and when Flogger
    // supports anonymous loggers it must ensure that this code path is avoided by not allowing
    // subclasses of Logger to be used.
    internal fun forceLoggingViaChildLogger(record: LogRecord) {
        val forcingLogger = getForcingLogger(logger)
        try {
            forcingLogger.level = Level.ALL.toJavaLogging()
        } catch (_: SecurityException) {
            cannotUseForcingLogger = true
            Logger.getLogger("").log(
                SEVERE.toJavaLogging(),
                """
                Forcing log statements with has been partially disabled.
                The Logging library cannot modify logger log levels, which is necessary to
                force log statements. This is likely due to an installed `SecurityManager`.
                Forced log statements will still be published directly to log handlers, but
                will not be visible to the `log(LogRecord)` method of `Logger` subclasses.
                """.trimIndent()
            )
            publish(logger, record)
            return
        }
        forcingLogger.log(record)
    }

    @VisibleForTesting
    internal open fun getForcingLogger(parent: Logger): Logger =
        Logger.getLogger(parent.name + ".__forced__")

    public companion object {
        @Volatile
        private var cannotUseForcingLogger: Boolean = false
    }
}
