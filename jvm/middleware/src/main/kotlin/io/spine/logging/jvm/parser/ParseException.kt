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

package io.spine.logging.jvm.parser

import java.io.Serial
import java.io.Serializable

/**
 * The exception that should be thrown whenever parsing of a log message fails.
 *
 * This exception must not be thrown outside of template parsing.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/ParseException.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public class ParseException private constructor(errorMessage: String) :
    RuntimeException(errorMessage), Serializable {

    /**
     * Disable expensive stack analysis because the parse exception contains everything it needs to
     * point the user at the proximal cause in the log message itself, and backends must always
     * wrap this in a [io.spine.logging.jvm.backend.LoggingException] if they do throw it up
     * into user code (not recommended).
     */
    @Synchronized
    override fun fillInStackTrace(): Throwable = this

    public companion object {

        /**
         * The prefix/suffix to show when an error snippet is truncated
         * (e.g., `"...ello [%Q] Worl..."`).
         *
         * If the snippet starts or ends the message then no ellipsis is shown
         * (e.g., `"...ndex=[%Q]"`).
         */
        private const val ELLIPSIS = "..."

        /**
         * The length of the snippet to show before and after the error.
         *
         * Fewer characters will be shown if the error is near the start/end of the log message and
         * more characters will be shown if adding the ellipsis would have made things longer.
         * The maximum prefix/suffix of the snippet is `(SNIPPET_LENGTH + ELLIPSIS.length())`.
         */
        private const val SNIPPET_LENGTH = 5

        @Serial
        private const val serialVersionUID = 0L

        /**
         * Creates a new parse exception for situations in which both the start and
         * end positions of the error are known.
         *
         * @param errorMessage The user error message.
         * @param logMessage The original log message.
         * @param start The index of the first character in the invalid section of the log message.
         * @param end The index after the last character in the invalid section of the log message.
         * @return the parser exception.
         */
        @JvmStatic
        public fun withBounds(
            errorMessage: String, logMessage: String, start: Int, end: Int
        ): ParseException {
            return ParseException(msg(errorMessage, logMessage, start, end))
        }

        /**
         * Creates a new parse exception for situations in which the position of the error is known.
         *
         * @param errorMessage The user error message.
         * @param logMessage The original log message.
         * @param position The index of the invalid character in the log message.
         * @return the parser exception.
         */
        @JvmStatic
        public fun atPosition(
            errorMessage: String,
            logMessage: String,
            position: Int
        ): ParseException =
            ParseException(msg(errorMessage, logMessage, position, position + 1))

        /**
         * Creates a new parse exception for situations in which only the start
         * position of the error is known.
         *
         * @param errorMessage The user error message.
         * @param logMessage The original log message.
         * @param start The index of the first character in the invalid section of the log message.
         * @return the parser exception.
         */
        @JvmStatic
        public fun withStartPosition(
            errorMessage: String, logMessage: String, start: Int
        ): ParseException =
            ParseException(msg(errorMessage, logMessage, start, -1))

        /**
         * Creates a new parse exception for cases where position is not relevant.
         *
         * @param errorMessage the user error message.
         */
        @JvmStatic
        internal fun generic(errorMessage: String): ParseException =
            ParseException(errorMessage)

        /**
         * Helper to format a human-readable error message for this exception.
         */
        private fun msg(
            errorMessage: String,
            logMessage: String,
            errorStart: Int,
            errorEnd: Int
        ): String {
            var end = errorEnd
            if (end < 0) {
                end = logMessage.length
            }
            val out = StringBuilder(errorMessage).append(": ")
            if (errorStart > SNIPPET_LENGTH + ELLIPSIS.length) {
                out.append(ELLIPSIS)
                   .append(logMessage, errorStart - SNIPPET_LENGTH, errorStart)
            } else {
                out.append(logMessage, 0, errorStart)
            }
            out.append('[')
               .append(logMessage.substring(errorStart, end))
               .append(']')
            if (logMessage.length - end > SNIPPET_LENGTH + ELLIPSIS.length) {
                out.append(logMessage, end, end + SNIPPET_LENGTH)
                   .append(ELLIPSIS)
            } else {
                out.append(logMessage, end, logMessage.length)
            }
            return out.toString()
        }
    }
}
