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

package io.spine.logging

/**
 * Represents the location of a single log statement.
 *
 * This type is used to identify a particular log statement and provide
 * a linkage between the statement itself and its metadata.
 *
 * For example, the logging [facade][LoggingApi] allows configuring of
 * a logging statement to be emitted only if a specific condition is satisfied.
 * Consider the [LoggingApi.atMostEvery] method, which configures a log statement
 * to perform actual logging no often than once per the specified period when
 * called multiple times. To achieve this, the facade needs to track
 * previous invocations, and this information is part of metadata that is stored
 * for each statement.
 *
 * Usually, this type if filled from a stack trace until it is injected
 * [manually][LoggingApi.withInjectedLogSite], or the used backend provides
 * its own mechanism to determine a log site.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogSite.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public interface LogSite {

    /**
     * Full name of the class containing the log statement.
     */
    public val className: String

    /**
     * Name of the method containing the log statement.
     */
    public val methodName: String

    /**
     * The name of the class file containing the log statement (or `null` if not known).
     *
     * The source file name is optional and strictly for debugging purposes.
     */
    public val fileName: String?

    /**
     * Line number of the log statement.
     */
    public val lineNumber: Int

    /**
     * A singleton instance used to indicate that valid log site information
     * cannot be determined.
     *
     * If a log statement ends up with invalid log site information, then any
     * fluent logging methods, which rely on being able to look up site-specific
     * metadata will be disabled and essentially become “no-op.”
     */
    public object Invalid : LogSite {
        override val className: String = "<unknown class>"
        override val methodName: String = "<unknown method>"
        override val fileName: String? = null
        override val lineNumber: Int = 0
    }
}
