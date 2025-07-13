/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend

import io.spine.logging.jvm.AbstractLogger
import java.util.logging.Level

/**
 * Interface for all logger backends.
 *
 * ### Implementation Notes
 *
 * Often each [AbstractLogger] instance will be instantiated with a new logger
 * backend to permit per-class logging behavior. Because of this, it is important
 * that logger backends have as little per-instance state as possible.
 *
 * It is also essential that no implementation of `LoggerBackend`
 * ever holds onto user supplied objects (especially log statement arguments)
 * after the `log()` or `handleError()` methods to which they
 * were passed have exited.
 *
 * This means that **ALL** formatting or serialization of log statement arguments or
 * metadata values **MUST** be completed inside the log method itself.
 * If the backend needs to perform asynchronous I/O operations it can do so
 * by constructing a serialized form of the [LogData] instance and
 * enqueing that for processing.
 *
 * Note also that this restriction is **NOT** purely about mutable arguments (which could
 * change before formatting occurs and produce incorrect output), but also stops log statements from
 * changing the lifetime of arbitrary user arguments, which can cause "use after close" bugs and
 * other garbage collector issues.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/LoggerBackend.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public abstract class LoggerBackend {
    
    /**
     * Returns the logger name (which is usually a canonicalized class name) or
     * `null` if not given.
     */
    public abstract val loggerName: String?

    /**
     * Returns whether logging is enabled for the given level for this backend.
     * Different backends may return different values depending on the class
     * with which they are associated.
     */
    public abstract fun isLoggable(level: Level): Boolean

    /**
     * Outputs the log statement represented by the given [LogData] instance.
     *
     * @param data user and logger supplied data to be rendered in a backend-specific way.
     *        References to `data` must not be held after the `log` invocation returns.
     */
    public abstract fun log(data: LogData)

    /**
     * Handles an error in a log statement.
     *
     * Errors passed into this method are expected to have only three distinct causes:
     *
     * 1. Bad format strings in log messages (e.g., `"foo=%Q"`.
     *    These are always instances of [ParseException] and contain
     *    human-readable error messages describing the problem.
     *
     * 2. A backend optionally choosing not to handle errors from user code during formatting.
     *    This is not recommended (see below) but may be useful in testing or debugging.
     *    
     * 3. Runtime errors in the backend itself.
     *
     * It is recommended that backend implementations avoid propagating exceptions in user code
     * (e.g., calls to `toString()`), as the nature of logging means that log statements are
     * often only enabled when debugging.
     *
     * If errors were propagated up into user code, enabling logging to look for
     * the cause of one issue could trigger previously unknown bugs, which could
     * then seriously hinder debugging the original issue.
     *
     * Typically, a backend would handle an error by logging an alternative representation of
     * the "bad" log data, being careful not to allow any more exceptions to occur.
     *
     * If a backend chooses to propagate an error (e.g., when testing or debugging)
     * it must wrap it in [LoggingException] to avoid it being re-caught.
     *
     * @param error the exception throw when `badData` was initially logged.
     * @param badData the original `LogData` instance which caused an error.
     *        It is not expected that simply trying to log this again will succeed, and error
     *        handlers must be careful in how they handle this instance, its arguments and metadata.
     *        References to `badData` must not be held after the `handleError` invocation returns.
     *
     * @throws LoggingException to indicate an error which should be propagated into user code.
     */
    public abstract fun handleError(error: RuntimeException, badData: LogData)
}
