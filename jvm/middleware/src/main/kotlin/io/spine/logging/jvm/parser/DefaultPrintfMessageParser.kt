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
import io.spine.logging.jvm.parameter.BraceStyleParameter
import io.spine.logging.jvm.parameter.DateTimeParameter
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * Default implementation of the printf message parser.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/DefaultPrintfMessageParser.java)
 * for historical context.
 */
public class DefaultPrintfMessageParser private constructor() : PrintfMessageParser() {

    @Suppress("ReturnCount")
    override fun getParameter(index: Int, formatChar: Char, options: FormatOptions): Parameter {
        // Handle %c and %C specifically (they are the only format specifiers which need to be
        // processed differently from normal printf formatting).
        if (formatChar == 'c' || formatChar == 'C') {
            return SimpleParameter.of(FormatChar.CHAR, options, index)
        }
        // Handle %t and %T specifically (date/time formatting).
        if (formatChar == 't' || formatChar == 'T') {
            return DateTimeParameter(formatChar, options, index)
        }
        // Everything else is just passed through to the formatter.
        val formatType = FormatChar.of(formatChar)
        return if (formatType != null) {
            SimpleParameter.of(formatType, options, index)
        } else {
            // This is a non-standard format specifier (e.g. %q) which won't be parsed by the
            // standard formatter, so we need to handle it differently. We know that the format
            // specifier is a letter (that's enforced by the parser) so we can just pass it as a
            // simple parameter.
            BraceStyleParameter(options, index)
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
