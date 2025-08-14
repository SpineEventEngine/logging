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

import io.spine.logging.KeyValueHandler

/**
 * Formats key/value pairs as a human-readable string on the end of log statements.
 *
 * The format is:
 *
 * ```
 *   Log Message PREFIX[ key1=value1 key2=value2 ]
 * ```
 *
 * or
 *
 * ```
 *   Multi line
 *   Log Message
 *   PREFIX[ key1=value1 key2=value2 ]
 * ```
 *
 * Note that:
 *
 * - Key/value pairs are appended in the order they are handled.
 * - If no key/value pairs are handled, the log message is unchanged (no prefix is added).
 * - Keys can be repeated.
 * - Key labels do not need quoting.
 * - String-like values are properly quoted and escaped (e.g. \", \\, \n, \t)
 * - Unsafe control characters in string-like values are replaced by U+FFFD (�).
 * - All key/value pairs are on the "same line" of the log message.
 *
 * The result is that this string should be fully reparsable (with the exception of replaced unsafe
 * characters) and easily searchable by text-based tools such as "grep".
 *
 * @param prefix The prefix to add before the key/value pairs (e.g., `[<prefix>[ foo=bar ]]`).
 * @param suffix The suffix to add after the key/value pairs.
 * @param out The buffer originally containing the log message, to which we append context.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/KeyValueFormatter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public class KeyValueFormatter(
    private val prefix: String,
    private val suffix: String,
    private val out: StringBuilder
) : KeyValueHandler {

    /**
     * Is `true` once we've handled at least one key/value pair.
     */
    private var haveSeenValues = false

    override fun handle(key: String, value: Any?) {
        if (haveSeenValues) {
            out.append(' ')
        } else {
            // At this point 'out' contains only the log message we are appending to.
            if (out.isNotEmpty()) {
                out.append(if (out.length > NEWLINE_LIMIT || out.indexOf("\n") != -1) '\n' else ' ')
            }
            out.append(prefix)
            haveSeenValues = true
        }
        appendJsonFormattedKeyAndValue(key, value, out)
    }

    /**
     * Terminates handling of key/value pairs, leaving the originally supplied buffer modified.
     */
    public fun done() {
        if (haveSeenValues) {
            out.append(suffix)
        }
    }

    public companion object {

        /**
         * If a single-line log message is > NEWLINE_LIMIT characters long, emit a newline first.
         *
         * Having a limit prevents scanning very large messages just to discover
         * they do not contain newlines.
         */
        private const val NEWLINE_LIMIT = 1000

        /**
         * All fundamental types other than "Character", since that can require escaping.
         */
        private val FUNDAMENTAL_TYPES = setOf(
            Boolean::class,
            Byte::class,
            Short::class,
            Int::class,
            Long::class,
            Float::class,
            Double::class,
        )

        /**
         * Helper method to emit metadata key/value pairs in a format consistent with JSON.
         *
         * String values which need to be quoted are JSON escaped, while other values are appended
         * without quoting or escaping. Labels are expected to be JSON "safe", and are never quoted.
         *
         * This format is compatible with various "lightweight" JSON representations.
         */
        @JvmStatic
        public fun appendJsonFormattedKeyAndValue(
            label: String,
            value: Any?,
            out: StringBuilder
        ) {
            out.append(label)
               .append('=')
            // We could also consider enums as safe if we used name() rather than toString().
            when {
                value == null -> {
                    // Alternately, emit the label without '=' to indicate presence without a value.
                    out.append(true)
                }
                FUNDAMENTAL_TYPES.contains(value::class) -> {
                    out.append(value)
                }
                else -> {
                    out.append('"')
                    appendEscaped(out, value.toString())
                    out.append('"')
                }
            }
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private fun appendEscaped(out: StringBuilder, s: String) {
            var start = 0
            // Most of the time this loop is executed zero times as there are no escapable chars.
            for (idx in nextEscapableChar(s, start)) {
                out.append(s, start, idx)
                start = idx + 1
                var c = s[idx]
                when (c) {
                    '"', '\\' -> {
                        // No character substitution needed
                    }
                    '\n' -> {
                        c = 'n'
                    }
                    '\r' -> {
                        c = 'r'
                    }
                    '\t' -> {
                        c = 't'
                    }
                    else -> {
                        // All that remains are unprintable ASCII control characters.
                        // It seems reasonable to replace them since the calling code is
                        // in complete control of these values, and they are meant
                        // to be human-readable.
                        // Use the Unicode replacement character '�'.
                        out.append('\uFFFD')
                        continue
                    }
                }
                out.append("\\")
                   .append(c)
            }
            out.append(s, start, s.length)
        }

        @Suppress("MagicNumber")
        private fun nextEscapableChar(s: String, n: Int): Sequence<Int> = sequence {
            for (i in n until s.length) {
                val c = s[i]
                if (c.code < 0x20 || c == '"' || c == '\\') {
                    yield(i)
                }
            }
        }
    }
}
