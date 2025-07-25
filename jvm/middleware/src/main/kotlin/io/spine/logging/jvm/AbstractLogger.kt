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

package io.spine.logging.jvm

import io.spine.logging.jvm.backend.LogData
import io.spine.logging.jvm.backend.LoggerBackend
import io.spine.logging.jvm.backend.LoggingException
import io.spine.logging.jvm.backend.appendLogSite
import io.spine.logging.jvm.util.RecursionDepth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.concurrent.TimeUnit.NANOSECONDS
import io.spine.logging.jvm.util.Checks.checkNotNull

/**
 * Base class for the fluent logging API.
 *
 * This class is a factory for instances of a logging API, used to build
 * log statements via method chaining.
 *
 * @param API The logging API provided by this logger.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/AbstractLogger.java">
 *    Original Java code of Google Flogger</a> for historical context.
 */
@Suppress("TooManyFunctions")
public abstract class AbstractLogger<API : MiddlemanApi<API>> protected constructor(
    private val backend: LoggerBackend
) {

    /**
     * An upper bound on the depth of reentrant logging allowed by a looger.
     *
     * Logger backends may choose to react to reentrant logging sooner than this,
     * but once this value is reached, a warning is emitted to stderr, which will not include
     * any user-provided arguments or metadata (in an attempt to halt recursion).
     */
    private companion object {
        private const val MAX_ALLOWED_RECURSION_DEPTH = 100

        @Suppress("SimpleDateFormatWithoutLocale")
        @JvmField
        val FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .withZone(ZoneId.systemDefault())
    }

    // ---- PUBLIC API ----

    /**
     * Returns a fluent logging API appropriate for the specified log level.
     *
     * If a logger implementation determines that logging is definitely disabled at this point then
     * this method is expected to return a "no-op" implementation of that logging API, which will
     * result in all further calls made for the log statement to being silently ignored.
     *
     * A simple implementation of this method in a concrete subclass might look like:
     * ```
     * val isLoggable = isLoggable(level)
     * val isForced = Platform.shouldForceLogging(getName(), level, isLoggable)
     * return if (isLoggable || isForced) SubContext(level, isForced) else NO_OP
     * ```
     * where `NO_OP` is a singleton, no-op instance of the logging API whose methods do
     * nothing and just `return noOp()`.
     */
    public abstract fun at(level: Level): API

    /** A convenience method for at([Level.SEVERE]). */
    public fun atSevere(): API = at(Level.SEVERE)

    /** A convenience method for at([Level.WARNING]). */
    public fun atWarning(): API = at(Level.WARNING)

    /** A convenience method for at([Level.INFO]). */
    public fun atInfo(): API = at(Level.INFO)

    /** A convenience method for at([Level.CONFIG]). */
    @Suppress("unused")
    public fun atConfig(): API = at(Level.CONFIG)

    /** A convenience method for at([Level.FINE]). */
    public fun atFine(): API = at(Level.FINE)

    /** A convenience method for at([Level.FINER]). */
    public fun atFiner(): API = at(Level.FINER)

    /** A convenience method for at([Level.FINEST]). */
    public fun atFinest(): API = at(Level.FINEST)

    // ---- HELPER METHODS (useful during sub-class initialization) ----

    /**
     * Returns the non-null name of this logger (Flogger does not currently support anonymous
     * loggers).
     */
    // IMPORTANT: Flogger does not currently support the idea of an anonymous logger instance
    // (but probably should). The issue here is that in order to allow the FluentLogger instance
    // and the LoggerConfig instance to share the same underlying logger, while allowing the
    // backend API to be flexible enough _not_ to admit the existence of the JDK logger, we will
    // need to push the LoggerConfig API down into the backend and expose it from there.
    // See b/14878562
    // TODO: Make anonymous loggers work with the config() method and the LoggerConfig API.
    public fun getName(): String {
        val loggerName = backend.loggerName
        return loggerName ?: "NULL_LOGGER"
    }

    /**
     * Returns whether the given level is enabled for this logger.
     *
     * Users wishing to guard code with a check for "loggability"
     * should use `logger.atLevel().isEnabled()` instead.
     */
    protected fun isLoggable(level: Level): Boolean = backend.isLoggable(level)

    /**
     * Opens access to the `protected` [isLoggable] function for this package.
     */
    internal fun doIsLoggable(level: Level): Boolean = isLoggable(level)

    /**
     * Returns the logging backend (not visible to logger subclasses to discourage tightly coupled
     * implementations).
     */
    public fun getBackend(): LoggerBackend = backend

    /**
     * Invokes the logging backend to write a log statement, ensuring that all exceptions which
     * could be caused during logging, including any subsequent error handling, are handled. This method
     * can only fail due to instances of [LoggingException] or [java.lang.Error] being thrown.
     *
     * This method also guards against unbounded reentrant logging, and will suppress further
     * logging if it detects significant recursion has occurred.
     */
    @Suppress("TooGenericExceptionCaught")
    public fun write(data: LogData) {
        checkNotNull(data, "data")
        // Note: Recursion checking should not be in the `LoggerBackend`.
        // There are many backends and they can call into other backends.
        // We only want the counter incremented per log statement.
        try {
            RecursionDepth.enterLogStatement().use { depth ->
                if (depth.getValue() <= MAX_ALLOWED_RECURSION_DEPTH) {
                    backend.log(data)
                } else {
                    reportError("unbounded recursion in log statement", data)
                }
            }
        } catch (logError: RuntimeException) {
            handleErrorRobustly(logError, data)
        }
    }

    /**
     * Only allow `LoggingException` and `Errors` to escape this method.
     */
    @Suppress("UseOfSystemOutOrSystemErr", "TooGenericExceptionCaught", "PrintStackTrace")
    private fun handleErrorRobustly(logError: RuntimeException, data: LogData) {
        try {
            backend.handleError(logError, data)
        } catch (allowed: LoggingException) {
            // Bypass the catch-all if the exception is deliberately created during error handling.
            throw allowed
        } catch (badError: RuntimeException) {
            // Don't trust exception toString() method here.
            reportError("${badError.javaClass.name}: ${badError.message}", data)
            // However printStackTrace() will invoke toString() on the exception and its causes.
            try {
                badError.printStackTrace(System.err)
            } catch (_: RuntimeException) {
                // We already printed the base error, so it doesn't seem worth doing
                // anything more here.
            }
        }
    }

    /**
     * It is important that this code never risk calling back to a user-supplied value
     * (e.g., logged arguments or metadata) since that could trigger a recursive error state.
     */
    private fun reportError(message: String, data: LogData) {
        val out = buildString {
            append(formatTimestampIso8601(data))
            append(": logging error [")
            appendLogSite(data.logSite)
            append("]: ")
            append(message)
        }
        @Suppress("UseOfSystemOutOrSystemErr")
        System.err.run {
            println(out)
            // We expect System.err to be an auto-flushing stream, but let's be sure.
            flush()
        }
    }

    private fun formatTimestampIso8601(data: LogData): String {
        val instant = Instant.ofEpochMilli(NANOSECONDS.toMillis(data.timestampNanos))
        return FORMATTER.format(instant)
    }
}
