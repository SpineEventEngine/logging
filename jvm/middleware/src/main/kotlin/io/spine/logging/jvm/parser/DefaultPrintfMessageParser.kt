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

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.backend.FormatOptions.Companion.FLAG_LEFT_ALIGN
import io.spine.logging.jvm.backend.FormatOptions.Companion.FLAG_UPPER_CASE
import io.spine.logging.jvm.backend.FormatOptions.Companion.parse
import io.spine.logging.jvm.parameter.ArgumentVisitor
import io.spine.logging.jvm.parameter.DateTimeFormat
import io.spine.logging.jvm.parameter.DateTimeParameter
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter.Companion.of
import io.spine.logging.jvm.parser.ParseException.Companion.atPosition
import io.spine.logging.jvm.parser.ParseException.Companion.withBounds

/**
 * Default implementation of the printf message parser.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/DefaultPrintfMessageParser.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public class DefaultPrintfMessageParser private constructor() : PrintfMessageParser() {

    @Suppress("MagicNumber")
    public override fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int {
        // Assume terms are single characters.

        var termEnd = formatStart + 1
        // This _must_ be an ASCII letter representing printf-like specifier (but need not be valid).
        val typeChar = message.get(formatStart)
        val isUpperCase = (typeChar.code and 0x20) == 0
        val options = parse(message, specStart, formatStart, isUpperCase)

        val parameter: Parameter
        val formatChar = FormatChar.of(typeChar)
        if (formatChar != null) {
            if (!options.areValidFor(formatChar)) {
                throw withBounds(
                    "invalid format specifier", message, termStart,
                    termEnd
                )
            }
            parameter = of(formatChar, options, index)
        } else if (typeChar == 't' || typeChar == 'T') {
            if (!options.validate(FLAG_LEFT_ALIGN or FLAG_UPPER_CASE, false)) {
                throw withBounds(
                    "invalid format specification", message, termStart, termEnd
                )
            }
            // Time/date format terms have an extra character in them.
            termEnd += 1
            if (termEnd > message.length) {
                throw atPosition("truncated format specifier", message, termStart)
            }
            val dateTimeFormat = DateTimeFormat.of(message.get(formatStart + 1))
            if (dateTimeFormat == null) {
                throw atPosition(
                    "illegal date/time conversion", message,
                    formatStart + 1
                )
            }
            parameter = DateTimeParameter(dateTimeFormat, options, index)
        } else if (typeChar == 'h' || typeChar == 'H') {
            // %h/%H is a legacy format we want to support for syntax compliance with String.format()
            // but which we don't need to support in the backend.
            if (!options.validate(FLAG_LEFT_ALIGN or FLAG_UPPER_CASE, false)) {
                throw withBounds(
                    "invalid format specification", message, termStart, termEnd
                )
            }
            parameter = wrapHexParameter(options, index)
        } else {
            throw withBounds(
                "invalid format specification", message, termStart, formatStart + 1
            )
        }
        builder.addParameter(termStart, termEnd, parameter)
        return termEnd
    }

    // Static method so the anonymous synthetic parameter is static, rather than an inner class.
    private fun wrapHexParameter(options: FormatOptions, index: Int): Parameter {

        // %h / %H is really just %x / %X on the hashcode.
        return object : Parameter(options, index) {
            override fun accept(visitor: ArgumentVisitor, value: Any) {
                visitor.visit(value.hashCode(), FormatChar.HEX, formatOptions)
            }

            override val format: String = if (options.shouldUpperCase()) "%H" else "%h"
        }
    }

    public companion object {

        private val INSTANCE = DefaultPrintfMessageParser()

        @JvmStatic
        public fun getInstance(): DefaultPrintfMessageParser {
            return INSTANCE
        }
    }
}
