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

import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter
import io.spine.logging.jvm.backend.FormatChar

/**
 * A message parser for brace style formatting (e.g. "{0} {1}").
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/BraceStyleMessageParser.java)
 * for historical context.
 */
public abstract class BraceStyleMessageParser : MessageParser() {

    @Throws(ParseException::class)
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.getMessage()
        var pos = 0
        while (pos < message.length) {
            val termStart = nextBraceFormatTerm(message, pos)
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
            // Skip over the '{' character.
            var specStart = termStart + 1
            // Look for the terminating '}' character.
            var specEnd = specStart
            while (specEnd < message.length && message[specEnd] != '}') {
                specEnd++
            }
            if (specEnd >= message.length) {
                throw ParseException.withStartPosition(
                    "unterminated brace format", message, termStart
                )
            }
            // Extract the index from the specification.
            val indexStr = message.substring(specStart, specEnd)
            try {
                val index = indexStr.toInt()
                if (index < 0) {
                    throw ParseException.withBounds(
                        "invalid index", message, specStart, specEnd
                    )
                }
                if (index >= MAX_ARG_COUNT) {
                    throw ParseException.withBounds(
                        "index too large", message, specStart, specEnd
                    )
                }
                val param = getParameter(index)
                builder.addParameter(termStart, specEnd + 1, param)
            } catch (e: NumberFormatException) {
                throw ParseException.withBounds(
                    "invalid index", message, specStart, specEnd
                )
            }
            pos = specEnd + 1
        }
        // Final part (will do nothing if pos >= message.length())
        if (pos < message.length) {
            builder.addParameter(
                pos, message.length,
                SimpleParameter.of(FormatChar.STRING, FormatOptions.getDefault(), -1)
            )
        }
    }

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapeBraceFormat(out, message, start, end)
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
     * Returns a parameter instance for the given brace format specification.
     *
     * @param index the argument index for the parameter
     */
    protected abstract fun getParameter(index: Int): Parameter
}

/**
 * Unescapes a brace format message, which just means replacing {{ with { and }} with }.
 */
@Suppress("ReturnCount", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
private fun unescapeBraceFormat(out: StringBuilder, message: String, start: Int, end: Int) {
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

