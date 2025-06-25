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

package io.spine.logging.jvm.backend;

import io.spine.logging.jvm.JvmLogSite;

import java.util.logging.Level;

/**
 * A backend API for determining metadata associated with a log statement.
 *
 * <p>
 * Some metadata is expected to be available for all log statements (such as the log level or a
 * timestamp) whereas other data is optional (class/method name for example). As well providing the
 * common logging metadata, customized loggers can choose to add arbitrary key/value pairs to the
 * log data. It is up to each logging backend implementation to decide how it interprets this data
 * using the hierarchical key. See {@link Metadata}.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/LogData.java">
 *      Original Java code of Google Flogger</a>
 */
public interface LogData {

    /** Returns the log level for the current log statement. */
    Level getLevel();

    /** Returns a nanosecond timestamp for the current log statement. */
    long getTimestampNanos();

    /**
     * Returns the logger name (which is usually a canonicalized class name) or {@code null}
     * if not given.
     */
    String getLoggerName();

    /**
     * Returns the log site data for the current log statement.
     *
     * @throws IllegalStateException
     *         if called prior to the postProcess() method being called.
     */
    JvmLogSite getLogSite();

    /**
     * Returns any additional metadata for this log statement. If no additional metadata is present,
     * the immutable empty metadata instance is returned.
     *
     * <p>IMPORTANT: The returned instance is restricted to metadata added at the log site, and will
     * not include any scoped metadata to be applied to the log statement. To process combined log
     * site and scoped metadata, obtain or create a {@link MetadataProcessor}.
     */
    Metadata getMetadata();

    /**
     * Returns whether this log statement should be emitted regardless of its log
     * level or any other properties.
     *
     * <p>This allows extensions of {@code LogContext} or {@code LoggingBackend} which implement
     * additional filtering or rate-limiting fluent methods to easily check whether a log statement
     * was forced. Forced log statements should behave exactly as if none of the filtering or
     * rate-limiting occurred, including argument validity checks.
     *
     * <p>Thus the idiomatic use of {@code wasForced()} is:
     * <pre>{@code
     * public API someFilteringMethod(int value) {
     *   if (wasForced()) {
     *     return api();
     *   }
     *   if (value < 0) {
     *     throw new IllegalArgumentException("Bad things ...");
     *   }
     *   // rest of method...
     * }
     * }</pre>
     *
     * <p>Checking for forced log statements before checking the validity of arguments provides a
     * last-resort means to mitigate cases in which syntactically incorrect log statements are only
     * discovered when they are enabled.
     */
    boolean wasForced();

    /**
     * Returns a template key for this log statement, or {@code null} if the statement does not
     * require formatting (in which case the message to be logged can be determined by calling
     * {@link #getLiteralArgument()}).
     */
    TemplateContext getTemplateContext();

    /**
     * Returns the arguments to be formatted with the message. Arguments exist when a {@code log()}
     * method with a format message and separate arguments was invoked.
     *
     * @throws IllegalStateException
     *         if no arguments are available (i.e., when there is no template context).
     */
    Object[] getArguments();

    /**
     * Returns the single argument to be logged directly when no arguments were provided.
     *
     * @throws IllegalStateException
     *         if no single literal argument is available (ie, when a template
     *         context exists).
     */
    Object getLiteralArgument();
}
