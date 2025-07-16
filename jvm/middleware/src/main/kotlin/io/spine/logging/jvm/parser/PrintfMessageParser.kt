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
import io.spine.logging.jvm.parser.PrintfMessageParser.Companion.safeSystemNewline

/**
 * A specialized [MessageParser] for processing log messages in printf style, as used by
 * [String.format]. This is an abstract parser which knows how to
 * process and extract placeholder terms at a high level, but does not impose its own semantics
 * for place-holder types.
 *
 * Typically, you should not subclass this class, but instead subclass
 * [DefaultPrintfMessageParser], which provides compatibility with [String.format].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/PrintfMessageParser.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class PrintfMessageParser : MessageParser() {

    /**
     * Parses a single printf-like term from a log message into a message template builder.
     *
     * A simple example of an implicit parameter (the argument index is not specified):
     *
     * ```
     * message: "Hello %s World"
     * termStart: 6 ───┚╿╿
     * specStart: 7 ────┤│
     * formatStart: 7 ──╯│
     * return: 8 ────────╯
     * ```
     *
     * If this case there is no format specification, so `specStart == formatStart`.
     *
     * A complex example with an explicit index:
     * ```
     * message: "Hello %2$10d World"
     * termStart: 6 ───┚  ╿ ╿╿
     * specStart: 9 ──────╯ ││
     * formatStart: 11 ─────╯│
     * return: 12 ───────────╯
     * ```
     *
     * Note that in this example the given index will be 1 (rather than 2) because
     * `printf` specifies indexes using a 1-based scheme, but internally they are 0-based.
     *
     * @param builder The message template builder.
     * @param index The zero-based argument index for the parameter.
     * @param message The complete log message string.
     * @param termStart The index of the initial '%' character that starts the term.
     * @param specStart The index of the first format specification character
     *        (after any optional index specification).
     * @param formatStart The index of the (first) format character in the term.
     * @return the index after the last character of the term.
     */
    @Throws(ParseException::class)
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

    @Throws(ParseException::class)
    @Suppress(
        "CyclomaticComplexMethod",
        "LoopWithTooManyJumpStatements",
        "NestedBlockDepth",
        "MagicNumber"
    )
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.message
        // The last index we used (needed for $< syntax, initially invalid).
        var lastResolvedIndex = -1
        // The next index to use for an implicit parameter
        // (can become -1 if a parameter consumes all the remaining arguments).
        var implicitIndex = 0
        // Find the start of each term in sequence.
        var pos = nextPrintfTerm(message, 0)
        while (pos >= 0) {
            // Capture the term start and move on (the character here is always '%').
            val termStart = pos++
            // At this stage we don't know if any numeric value we parse is going to be part of a
            // parameter index ($nnn$x) or the format specification (%nnnx)
            // but we assume the latter.
            var optionsStart = pos

            // STEP 1: Parse any numeric value at the start of the term.
            var c: Char
            var index = 0
            while (true) {
                if (pos < message.length) {
                    c = message.get(pos++)
                    // Casting to char makes the result unsigned
                    // (so we don't need to test `digit < 0` later).
                    val digit = (c.code - '0'.code).toChar().code
                    if (digit < 10) {
                        index = (10 * index) + digit
                        if (index < MAX_ARG_COUNT) {
                            continue
                        }
                        throw withBounds("index too large", message, termStart, pos)
                    }
                    // We found something other than [0-9] so we've finished parsing our value.
                    break
                }
                throw withStartPosition("unterminated parameter", message, termStart)
            }

            // STEP 2: Process the value and determine the parameter's real index.
            if (c == '$') {
                // If was an index, but we could have got here without parsing any digits
                // (i.e., `"%$"`)
                val indexLen = (pos - 1) - optionsStart
                if (indexLen == 0) {
                    throw withBounds("missing index", message, termStart, pos)
                }
                // We also prohibit leading zeros in any index value (`printf` indices are 1-based,
                // so the first digit should never be zero).
                if (message.get(optionsStart) == '0') {
                    throw withBounds("index has leading zero", message, termStart, pos)
                }
                // Now correct the index to be 0-based.
                index -= 1
                // Having got the parameter index, reset the specification start to
                // just after the '$'.
                optionsStart = pos
                // Read the next character from the message
                // (needed for the next part of the parsing).
                if (pos == message.length) {
                    throw withStartPosition("unterminated parameter", message, termStart)
                }
                pos++
            } else if (c == '<') {
                // This is the rare 'relative' indexing mode where
                // you just re-use the last parameter index.
                if (lastResolvedIndex == -1) {
                    throw withBounds("invalid relative parameter", message, termStart, pos)
                }
                index = lastResolvedIndex
                // Having got the parameter index, reset the specification start
                // to just after the '<'.
                optionsStart = pos
                // Read the next character from the message
                // (needed for the next part of the parsing).
                if (pos == message.length) {
                    throw withStartPosition("unterminated parameter", message, termStart)
                }
                pos++
            } else {
                // The parsed value was not an index, so we use the current implicit index.
                // We do not need to update the format start in this case, and
                // the current character is already correct for
                // the next part of the parsing.
                index = implicitIndex++
            }

            // STEP 3: Find the index of the type character that terminates the format
            // specification. Remember to decrement pos to account for the fact we were
            // one ahead of the current char.
            pos = findFormatChar(message, termStart, pos - 1)

            // STEP 4: Invoke the term parsing method and reset loop state.
            // Add a parameter to the builder and find where the term ends.
            pos = parsePrintfTerm(builder, index, message, termStart, optionsStart, pos)
            // Before going round again, record the index we just used and update
            // the implicit index.
            lastResolvedIndex = index
            pos = nextPrintfTerm(message, pos)
        }
    }

    internal companion object {

        /**
         * Assume that anything outside this set of chars is suspicious and not safe.
         */
        private const val ALLOWED_NEWLINE_PATTERN = "\\n|\\r(?:\\n)?"

        /**
         * The system newline separator for replacing `%n`.
         */
        val safeSystemNewline: String by lazy {
            computeSafeSystemNewline()
        }

        /**
         * Returns the system newline separator avoiding any issues with
         * security exceptions or "suspicious" values.
         *
         * The only allowed return values are `"\n"` (default), `"\r"` or `"\r\n"`.
         */
        @Suppress("SystemGetProperty", "SwallowedException")
        private fun computeSafeSystemNewline(): String {
            try {
                val unsafeNewline = System.getProperty("line.separator")
                if (unsafeNewline.matches(ALLOWED_NEWLINE_PATTERN.toRegex())) {
                    return unsafeNewline
                }
            } catch (_: SecurityException) {
                // Fall through to default value;
            }
            return "\n"
        }
    }
}

/**
 * Returns the index of the first unescaped '%' character in a message
 * starting at pos (or —1, if not found).
 */
@VisibleForTesting
@Throws(ParseException::class)
@Suppress("LoopWithTooManyJumpStatements")
internal fun nextPrintfTerm(message: String, pos: Int): Int {
    var pos = pos
    while (pos < message.length) {
        if (message.get(pos++) != '%') {
            continue
        }
        if (pos < message.length) {
            val c = message.get(pos)
            if (c == '%' || c == 'n') {
                // We encountered '%%' or '%n', so keep going
                // (these will be unescaped later).
                pos += 1
                continue
            }
            // We were pointing at the character after the '%',
            // so adjust back by one.
            return pos - 1
        }
        // We ran off the end while looking for the character after the first '%'.
        throw withStartPosition("trailing unquoted '%' character", message, pos - 1)
    }
    // We never found another unescaped '%'.
    return -1
}

@Throws(ParseException::class)
@Suppress("MagicNumber")
private fun findFormatChar(message: String, termStart: Int, pos: Int): Int {
    var pos = pos
    while (pos < message.length) {
        val c = message[pos]
        // Get the relative offset of the ASCII letter (in the range 0-25) ignoring
        // whether it is an upper or lower case.
        // Using this unsigned value avoids multiple range checks in a tight loop.
        val alpha = ((c.code and 0x20.inv()) - 'A'.code).toChar().code
        if (alpha < 26) {
            return pos
        }
        pos++
    }
    throw withStartPosition("unterminated parameter", message, termStart)
}

/**
 * Unescapes the characters in the sub-string `s.substring(start, end)` according to
 * printf style formatting rules.
 */
@VisibleForTesting
@Suppress("MagicNumber", "LoopWithTooManyJumpStatements")
internal fun unescapePrintf(out: StringBuilder, message: String, start: Int, end: Int) {
    var start = start
    var pos = start
    while (pos < end) {
        if (message.get(pos++) != '%') {
            continue
        }
        if (pos == end) {
            // Ignore unexpected trailing '%'.
            break
        }
        val chr = message.get(pos)
        if (chr == '%') {
            // Append the section up to and including the first `'%'`.
            out.append(message, start, pos)
        } else if (chr == 'n') {
            // `%n` encountered, rewind one position to not emit leading `'%'` and emit a newline.
            out.append(message, start, pos - 1)
            out.append(safeSystemNewline)
        } else {
            // A single unescaped '%' is ignored and left in the output as-is.
            continue
        }
        // Increment the position and reset the start point after the last processed character.
        start = ++pos
    }
    // Append the last section (if it is non-empty).
    if (start < end) {
        out.append(message, start, end)
    }
}
