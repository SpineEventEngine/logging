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

import com.google.common.annotations.VisibleForTesting
import io.spine.logging.jvm.parser.ParseException.Companion.withBounds
import io.spine.logging.jvm.parser.ParseException.Companion.withStartPosition

/**
 * A message parser for brace style formatting (e.g. "{0} {1}").
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/BraceStyleMessageParser.java)
 * for historical context.
 */
public abstract class BraceStyleMessageParser : MessageParser() {

    @VisibleForTesting
    internal companion object {

        /**
         * The character used to delimit the argument index from the trailing part in brace style
         * formatting.
         */
        internal const val BRACE_STYLE_SEPARATOR: Char = ','
    }

    /**
     * Parses a single brace format term from a log message into a message template builder. Note that
     * the default brace style parser currently does not handle anything other than the simplest "{n}"
     * forms of parameter specification, and it will treat anything more complezx as a parsing error.
     *
     * A simple example of a positional parameter:
     * ```
     * message: "Hello {0} World"
     * termStart: 6 ───┚  ╿
     * formatStart: -1    │
     * termEnd: 9 ────────╯
     * ```
     * A more complex example with a trailing format specification:
     * ```
     * message: "Hello {0,number,#} World"
     * termStart: 6 ───┚  ╿        ╿
     * formatStart: 9 ────╯        │
     * termEnd: 18 ────────────────╯
     * ```
     * @param builder the message template builder.
     * @param index the zero-based argument index for the parameter.
     * @param message the complete log message string.
     * @param termStart the index of the initial '{' character that starts the term.
     * @param formatStart the index of the optional formatting substring after the first comma
     * (which extends to `termEnd - 1`) or -1 if there is no formatting substring.
     *
     * @param termEnd the index after the final '}' character that completes this term.
     */
    @Throws(ParseException::class)
    @Suppress("LongParameterList")
    internal abstract fun parseBraceFormatTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        formatStart: Int,
        termEnd: Int
    )

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapeBraceFormat(out, message, start, end)
    }

    @Throws(ParseException::class)
    @Suppress(
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "MagicNumber",
        "LoopWithTooManyJumpStatements"
    )
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.message
        var pos: Int = nextBraceFormatTerm(message, 0)
        while (pos >= 0
        ) {
            // Capture the term start and move on (the character here is always '%').
            val termStart = pos++
            // For brace format strings we know there must be an index and it starts just after the '{'.
            val indexStart = termStart + 1

            // STEP 1: Parse the numeric value at the start of the term.
            var c: Char
            var index = 0
            while (true) {
                if (pos < message.length) {
                    // Casting to char makes the result unsigned, so we don't need to test "digit < 0" later.
                    c = message.get(pos++)
                    val digit = (c.code - '0'.code).toChar().code
                    if (digit < 10) {
                        index = (10 * index) + digit
                        if (index < MAX_ARG_COUNT) {
                            continue
                        }
                        throw withBounds("index too large", message, indexStart, pos)
                    }
                    break
                }
                throw withStartPosition("unterminated parameter", message, termStart)
            }

            // Note that we could have got here without parsing any digits.
            val indexLen = (pos - 1) - indexStart
            if (indexLen == 0) {
                // We might want to support "{}" as the implicit placeholder one day.
                throw withBounds("missing index", message, termStart, pos)
            }
            // Indices are zero based so we can have a leading zero, but only if it's the only digit.
            if (message.get(indexStart) == '0' && indexLen > 1) {
                throw withBounds("index has leading zero", message, indexStart, pos - 1)
            }

            // STEP 2: Determine it there's a trailing part to the term.
            val trailingPartStart: Int
            if (c == '}') {
                // Well formatted without a separator: "{nn}"
                trailingPartStart = -1
            } else if (c == BRACE_STYLE_SEPARATOR) {
                trailingPartStart = pos
                do {
                    if (pos == message.length) {
                        throw withStartPosition("unterminated parameter", message, termStart)
                    }
                } while (message.get(pos++) != '}')
                // Well formatted with trailing part.
            } else {
                throw withBounds("malformed index", message, termStart + 1, pos)
            }

            // STEP 3: Invoke the term parsing method.
            parseBraceFormatTerm(builder, index, message, termStart, trailingPartStart, pos)
            pos = nextBraceFormatTerm(message, pos)
        }
    }
}

/**
 * Returns the index of the next brace term in a message or -1 if not found.
 */
@Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
public fun nextBraceFormatTerm(message: String, pos: Int): Int {
    var index = pos
    while (index < message.length) {
        if (message[index] == '{') {
            // Check if we have "{{" in which case we need to skip over the first one.
            if (index + 1 < message.length && message[index + 1] == '{') {
                index += 2
                continue
            }
            return index
        }
        if (message[index] == '}') {
            // Check if we have "}}" in which case we need to skip over the first one.
            if (index + 1 < message.length && message[index + 1] == '}') {
                index += 2
                continue
            }
            throw ParseException.atPosition(
                "unmatched closing brace", message, index
            )
        }
        index++
    }
    return -1
}

/**
 * Unescapes a brace format message, which just means replacing {{ with { and }} with }.
 */
@Suppress("ReturnCount", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
internal fun unescapeBraceFormat(out: StringBuilder, message: String, start: Int, end: Int) {
    var pos = start
    while (pos < end) {
        val c = message[pos]
        if (c == '{' || c == '}') {
            // Check if we have "{{" or "}}" in which case we need to output just one.
            if (pos + 1 < end && message[pos + 1] == c) {
                out.append(c)
                pos += 2
                continue
            }
            // Otherwise we have a real format specifier, which we don't want to unescape.
            if (c == '{') {
                val termStart = pos
                // Skip over the '{' character.
                pos++
                // Look for the terminating '}' character.
                while (pos < end && message[pos] != '}') {
                    pos++
                }
                if (pos >= end) {
                    throw ParseException.withStartPosition(
                        "unterminated brace format", message, termStart
                    )
                }
                // Skip over the '}' character.
                pos++
                continue
            }
            throw ParseException.atPosition("unmatched closing brace", message, pos)
        }
        out.append(c)
        pos++
    }
}

