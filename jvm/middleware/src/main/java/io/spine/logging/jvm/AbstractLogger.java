/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm;

import io.spine.logging.jvm.backend.LogData;
import io.spine.logging.jvm.backend.LoggerBackend;
import io.spine.logging.jvm.backend.LoggingException;
import io.spine.logging.jvm.backend.MessageUtils;
import io.spine.logging.jvm.util.RecursionDepth;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import static io.spine.logging.jvm.util.Checks.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Base class for the fluent logger API. This class is a factory for instances of a fluent logging
 * API, used to build log statements via method chaining.
 *
 * @param <API>
 *         the logging API provided by this logger.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/AbstractLogger.java">
 *      Original Java code of Google Flogger</a>
 */
public abstract class AbstractLogger<API extends JvmApi<API>> {

    /**
     * An upper bound on the depth of reentrant logging allowed by Flogger. Logger backends may
     * choose
     * to react to reentrant logging sooner than this, but once this value is reached, a warning is
     * is emitted to stderr, which will not include any user provided arguments or metadata (in an
     * attempt to halt recursion).
     */
    private static final int MAX_ALLOWED_RECURSION_DEPTH = 100;

    @SuppressWarnings("SimpleDateFormatWithoutLocale")
    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                             .withZone(ZoneId.systemDefault());

    private final LoggerBackend backend;

    /**
     * Constructs a new logger for the specified backend.
     *
     * @param backend
     *         the logger backend which ultimately writes the log statements out.
     */
    protected AbstractLogger(LoggerBackend backend) {
        this.backend = checkNotNull(backend, "backend");
    }

    // ---- PUBLIC API ----

    /**
     * Returns a fluent logging API appropriate for the specified log level.
 *
     * <p>
     * If a logger implementation determines that logging is definitely disabled at this point then
     * this method is expected to return a "no-op" implementation of that logging API, which will
     * result in all further calls made for the log statement to being silently ignored.
 *
     * <p>
     * A simple implementation of this method in a concrete subclass might look like:
     * <pre>{@code
     * boolean isLoggable = isLoggable(level);
     * boolean isForced = Platform.shouldForceLogging(getName(), level, isLoggable);
     * return (isLoggable | isForced) ? new SubContext(level, isForced) : NO_OP;
     * }</pre>
     * where {@code NO_OP} is a singleton, no-op instance of the logging API whose methods do
     * nothing
     * and just {@code return noOp()}.
     */
    public abstract API at(Level level);

    /** A convenience method for at({@link Level#SEVERE}). */
    public final API atSevere() {
        return at(Level.SEVERE);
    }

    /** A convenience method for at({@link Level#WARNING}). */
    public final API atWarning() {
        return at(Level.WARNING);
    }

    /** A convenience method for at({@link Level#INFO}). */
    public final API atInfo() {
        return at(Level.INFO);
    }

    /** A convenience method for at({@link Level#CONFIG}). */
    @SuppressWarnings("unused")
    public final API atConfig() {
        return at(Level.CONFIG);
    }

    /** A convenience method for at({@link Level#FINE}). */
    public final API atFine() {
        return at(Level.FINE);
    }

    /** A convenience method for at({@link Level#FINER}). */
    public final API atFiner() {
        return at(Level.FINER);
    }

    /** A convenience method for at({@link Level#FINEST}). */
    public final API atFinest() {
        return at(Level.FINEST);
    }

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
    // TODO(dbeaumont): Make anonymous loggers work with the config() method and the LoggerConfig API.
    protected String getName() {
        return backend.getLoggerName();
    }

    /**
     * Returns whether the given level is enabled for this logger. Users wishing to guard code with
     * a
     * check for "loggability" should use {@code logger.atLevel().isEnabled()} instead.
     */
    protected final boolean isLoggable(Level level) {
        return backend.isLoggable(level);
    }

    // ---- IMPLEMENTATION DETAIL (only visible to the base logging context) ----

    /**
     * Returns the logging backend (not visible to logger subclasses to discourage tightly coupled
     * implementations).
     */
    final LoggerBackend getBackend() {
        return backend;
    }

    /**
     * Invokes the logging backend to write a log statement, ensuring that all exceptions which
     * could
     * be caused during logging, including any subsequent error handling, are handled. This method
     * can
     * only fail due to instances of {@link LoggingException} or {@link java.lang.Error} being
     * thrown.
     *
     * <p>This method also guards against unbounded reentrant logging, and will suppress further
     * logging if it detects significant recursion has occurred.
     */
    final void write(LogData data) {
        checkNotNull(data, "data");
        // Note: Recursion checking should not be in the LoggerBackend. There are many backends and they
        // can call into other backends. We only want the counter incremented per log statement.
        try (RecursionDepth depth = RecursionDepth.enterLogStatement()) {
            if (depth.getValue() <= MAX_ALLOWED_RECURSION_DEPTH) {
                backend.log(data);
            } else {
                reportError("unbounded recursion in log statement", data);
            }
        } catch (RuntimeException logError) {
            handleErrorRobustly(logError, data);
        }
    }

    /** Only allow LoggingException and Errors to escape this method. */
    private void handleErrorRobustly(RuntimeException logError, LogData data) {
        try {
            backend.handleError(logError, data);
        } catch (LoggingException allowed) {
            // Bypass the catch-all if the exception is deliberately created during error handling.
            throw allowed;
        } catch (RuntimeException badError) {
            // Don't trust exception toString() method here.
            reportError(badError.getClass()
                                .getName() + ": " + badError.getMessage(), data);
            // However printStackTrace() will invoke toString() on the exception and its causes.
            try {
                badError.printStackTrace(System.err);
            } catch (RuntimeException ignored) {
                // We already printed the base error so it doesn't seem worth doing anything more here.
            }
        }
    }

    // It is important that this code never risk calling back to a user supplied value (e.g. logged
    // arguments or metadata) since that could trigger a recursive error state.
    private static void reportError(String message, LogData data) {
        StringBuilder out = new StringBuilder();
        out.append(formatTimestampIso8601(data))
           .append(": logging error [");
        MessageUtils.appendLogSite(data.getLogSite(), out);
        out.append("]: ")
           .append(message);
        System.err.println(out);
        // We expect System.err to be an auto-flushing stream, but let's be sure.
        System.err.flush();
    }

    private static String formatTimestampIso8601(LogData data) {
        var instant = Instant.ofEpochMilli(NANOSECONDS.toMillis(data.getTimestampNanos()));
        return FORMATTER.format(instant);
    }
}
