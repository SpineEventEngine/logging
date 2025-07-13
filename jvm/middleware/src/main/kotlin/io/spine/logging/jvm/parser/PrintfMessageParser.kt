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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * Returns the index of the format character in a printf term.
 */
private fun findFormatChar(message: String, pos: Int): Int {
    var index = pos
    while (index < message.length) {
        val c = message[index]
        if (c.isLetter() || c == '%') {
            return index
        }
        index++
    }
    // We hit the end of the message without finding a format character.
    return index
}

/**
 * A message parser for printf-like formatting. This parser handles format specifiers of the form
 * "%\[flags\]\[width\]\[.precision\]conversion".
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/PrintfMessageParser.java)
 * for historical context.
 */
public abstract class PrintfMessageParser : MessageParser() {

    public companion object {
        // Assume that anything outside this set of chars is suspicious and not safe.
        private  const val ALLOWED_NEWLINE_PATTERN: String = "\\n|\\r(?:\\n)?"

        /**
         * Returns the system newline separator avoiding any issues with security exceptions or
         * "suspicious" values. The only allowed return values are "\n" (default), "\r" or "\r\n".
         */
        public val SYSTEM_NEWLINE: String by lazy {
            getSafeSystemNewline()
        }

        private fun getSafeSystemNewline(): String {
            try {
                val unsafeNewline = System.lineSeparator()
                if (unsafeNewline.matches(ALLOWED_NEWLINE_PATTERN.toRegex())) {
                    return unsafeNewline
                }
            } catch (_: SecurityException) {
                // Fall through to default value;
            }
            return "\n"
        }
    }

    /**
     * Parses a single printf-like term from a log message into a message template builder.
     *
     * A simple example of an implicit parameter (the argument index is not specified):
     * ```
     * message: "Hello %s World"
     * termStart: 6 ───┚╿╿
     * specStart: 7 ────┤│
     * formatStart: 7 ──╯│
     * return: 8 ────────╯
     * ```
     * If this case there is no format specification, so `specStart == formatStart`.
     *
     *
     *
     * A complex example with an explicit index:
     * ```
     * message: "Hello %2$10d World"
     * termStart: 6 ───┚  ╿ ╿╿
     * specStart: 9 ──────╯ ││
     * formatStart: 11 ─────╯│
     * return: 12 ───────────╯
     * ```
     * Note that in this example the given index will be 1 (rather than 2) because printf specifies
     * indices using a 1-based scheme, but internally they are 0-based.
     *
     * @param builder The message template builder.
     * @param index The zero-based argument index for the parameter.
     * @param message The complete log message string.
     * @param termStart The index of the initial '%' character that starts the term.
     * @param specStart The index of the first format specification character (after any optional
     *   index specification).
     *
     * @param formatStart The index of the (first) format character in the term.
     * @return The index after the last character of the term.
     */
    @Suppress("LongParameterList")
    internal abstract fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapePrintf(out, message, start, end)
    }

    @Suppress("NestedBlockDepth")
    @Throws(ParseException::class)
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.message
        var pos = 0
        var index = 0
        while (pos < message.length) {
            val termStart = nextPrintfTerm(message, pos)
            if (termStart == -1) {
                break
            }
            // Append everything from the current position up to the term start.
            if (termStart > pos) {
                builder.addParameter(
                    pos, termStart,
                    SimpleParameter.of(FormatChar.STRING, FormatOptions.getDefault(), -1)
                )
            }
            // Skip over the '%' character.
            var specStart = termStart + 1
            // Look for argument indices of the form '%n$X' (this is not quite the full grammar).
            if (specStart < message.length) {
                var argIndexEnd = specStart
                while (argIndexEnd < message.length && message[argIndexEnd].isDigit()) {
                    argIndexEnd++
                }
                if (argIndexEnd > specStart
                    && argIndexEnd < message.length
                    && message[argIndexEnd] == '$'
                ) {
                    try {
                        val argIndex = message.substring(specStart, argIndexEnd).toInt() - 1
                        if (argIndex < 0) {
                            throw ParseException.withBounds(
                                "invalid format argument index", message, specStart, argIndexEnd
                            )
                        }
                        index = argIndex
                        // Skip over the "n$" part to get to the format specification.
                        specStart = argIndexEnd + 1
                    } catch (e: NumberFormatException) {
                        throw ParseException.withBounds(
                            "invalid format argument index", message, specStart, argIndexEnd
                        )
                    }
                }
            }
            val formatStart = findFormatChar(message, specStart)
            pos = parsePrintfTerm(builder, index++, message, termStart, specStart, formatStart)
        }
        // Final part (will do nothing if pos >= message.length())
        if (pos < message.length) {
            builder.addParameter(
                pos, message.length,
                SimpleParameter.of(FormatChar.STRING, FormatOptions.getDefault(), -1)
            )
        }
    }
}

/**
 * Unescapes a printf style message, which just means replacing %% with %.
 */
@Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
internal fun unescapePrintf(out: StringBuilder, message: String, start: Int, end: Int) {
    var pos = start
    while (pos < end) {
        val termStart = nextPrintfTerm(message, pos)
        if (termStart == -1 || termStart >= end) {
            break
        }
        // Append everything from the current position up to the term start.
        if (termStart > pos) {
            out.append(message, pos, termStart)
        }
        // Skip over the '%' character.
        pos = termStart + 1
        // If we have "%%" we need to output a single '%' and continue.
        if (pos < end && message[pos] == '%') {
            out.append('%')
            pos++
            continue
        }
        // Otherwise we have a real format specifier, which we don't want to unescape.
        out.append('%')
    }
    // Final part (will do nothing if pos >= end).
    if (pos < end) {
        out.append(message, pos, end)
    }
}

/**
 * Returns the index of the next printf term in a message or -1 if not found.
 */
@Suppress("ReturnCount")
public fun nextPrintfTerm(message: String, pos: Int): Int {
    var index = pos
    while (index < message.length) {
        if (message[index] == '%') {
            // Check if we have "%%" in which case we need to skip over the first one.
            if (index + 1 < message.length && message[index + 1] == '%') {
                index += 2
                continue
            }
            return index
        }
        index++
    }
    return -1
}
