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

package io.spine.logging.jvm.backend

import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * API for formatting Flogger log messages from logData and scoped metadata.
 *
 * This API is not used directly in the core Flogger libraries yet, but will become part of the
 * log message formatting API eventually. For now it should be considered an implementation detail
 * and definitely unstable.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/LogMessageFormatter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class LogMessageFormatter {

    // TODO(dbeaumont): This [class] needs to either move into "system" or be extended somehow.
    // This is currently tightly coupled with the JDK log handler behaviour
    // (by virtue of what data is expected to be used for formatting) so it is
    // not suitable as a general purpose API yet.

    /**
     * Returns a formatted representation of the log message and metadata.
     *
     * Currently, this class is only responsible for formatting the main body of the log
     * message and not things like log site, timestamps or thread information.
     *
     * By default, this method returns:
     *
     * ```kotlin
     * append(logData, metadata, StringBuilder()).toString()
     * ```
     *
     * Formatter implementations may be able to implement it more efficiently
     * (e.g., if they can safely detect when no formatting is required).
     * See also the helper methods in `[SimpleMessageFormatter]`.
     *
     * @see SimpleMessageFormatter
     */
    public open fun format(logData: LogData, metadata: MetadataProcessor): String =
        append(logData, metadata, StringBuilder()).toString()

    /**
     * Formats the log message and metadata into the given buffer.
     *
     * Currently, this class is only responsible for formatting the main body of
     * the log message and not things like log site, timestamps or thread information.
     *
     * @return the given buffer for method chaining.
     */
    @CanIgnoreReturnValue
    public abstract fun append(
        logData: LogData,
        metadata: MetadataProcessor,
        buffer: StringBuilder
    ): StringBuilder
}
