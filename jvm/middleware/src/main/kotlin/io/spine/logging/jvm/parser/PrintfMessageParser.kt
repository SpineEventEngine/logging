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
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * A message parser for printf-like formatting. This parser handles format specifiers of the form
 * "%\[flags\]\[width\]\[.precision\]conversion".
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/PrintfMessageParser.java)
 * for historical context.
 */
public abstract class PrintfMessageParser : MessageParser() {

    /**
     * Parses a printf term of the form "%\[flags\]\[width\]\[.precision\]conversion".
     *
     * @param builder The message builder
     * @param index The argument index for the parsed term
     * @param message The message being parsed
     * @param termStart The start of the term (the index of the '%' character)
     * @param specStart The start of the format specification (after any argument index)
     * @param formatStart The index of the format character
     * @return the index after the end of the term
     */
    @Suppress("ReturnCount", "LongParameterList")
    protected fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int {
        if (formatStart >= message.length) {
            throw ParseException.withStartPosition(
                "missing format specifier", message, termStart
            )
        }
        val formatChar = message[formatStart]
        if (formatChar == '%') {
            // Literal '%' which doesn't consume any arguments.
            builder.addParameter(
                termStart, formatStart + 1,
                SimpleParameter.of(FormatChar.STRING, FormatOptions.getDefault(), -1)
            )
            return formatStart + 1
        }
        if (formatChar == 'n') {
            // System newline which doesn't consume any arguments.
            builder.addParameter(
                termStart, formatStart + 1,
                SimpleParameter.of(FormatChar.STRING, FormatOptions.getDefault(), -1)
            )
            return formatStart + 1
        }
        val options = FormatOptions.parse(message, specStart, formatStart, formatChar.isUpperCase())
        val param = getParameter(index, formatChar, options)
        builder.addParameter(termStart, formatStart + 1, param)
        return formatStart + 1
    }

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapePrintf(out, message, start, end)
    }

    @Suppress("NestedBlockDepth")
    @Throws(ParseException::class)
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.getMessage()
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
     * Unescapes a printf style message, which just means replacing %% with %.
     */
    @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
    private fun unescapePrintf(out: StringBuilder, message: String, start: Int, end: Int) {
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
     * Returns a parameter instance for the given printf format specification.
     *
     * @param index the argument index for the parameter
     * @param formatChar the format character (e.g. 'd' or 's')
     * @param options the parsed format options
     */
    protected abstract fun getParameter(
        index: Int,
        formatChar: Char,
        options: FormatOptions
    ): Parameter
}
