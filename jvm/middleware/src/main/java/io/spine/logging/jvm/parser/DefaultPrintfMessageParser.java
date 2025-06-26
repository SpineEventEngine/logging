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

package io.spine.logging.jvm.parser;

import io.spine.logging.jvm.backend.FormatChar;
import io.spine.logging.jvm.backend.FormatOptions;
import io.spine.logging.jvm.parameter.DateTimeFormat;
import io.spine.logging.jvm.parameter.DateTimeParameter;
import io.spine.logging.jvm.parameter.Parameter;
import io.spine.logging.jvm.parameter.ParameterVisitor;
import io.spine.logging.jvm.parameter.SimpleParameter;

import static io.spine.logging.jvm.backend.FormatOptions.FLAG_LEFT_ALIGN;
import static io.spine.logging.jvm.backend.FormatOptions.FLAG_UPPER_CASE;

/**
 * Default implementation of the printf message parser. This parser supports all the place-holders
 * available in {@code String#format} but can be extended, if desired, for additional behavior
 * For consistency it is recommended, but not required, that custom printf parsers always extend
 * from this class.
 *
 * <p>
 * This class is immutable and thread safe (and any subclasses must also be so).
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/DefaultPrintfMessageParser.java">
 *      Original Java code of Google Flogger</a> for historical context.
 */
public class DefaultPrintfMessageParser extends PrintfMessageParser {

    private static final PrintfMessageParser INSTANCE = new DefaultPrintfMessageParser();

    public static PrintfMessageParser getInstance() {
        return INSTANCE;
    }

    private DefaultPrintfMessageParser() {
    }

    @Override
    public int parsePrintfTerm(
            MessageBuilder<?> builder,
            int index,
            String message,
            int termStart,
            int specStart,
            int formatStart)
            throws ParseException {

        // Assume terms are single characters.
        var termEnd = formatStart + 1;
        // This _must_ be an ASCII letter representing printf-like specifier (but need not be valid).
        var typeChar = message.charAt(formatStart);
        var isUpperCase = (typeChar & 0x20) == 0;
        var options = FormatOptions.parse(message, specStart, formatStart, isUpperCase);

        Parameter parameter;
        var formatChar = FormatChar.of(typeChar);
        if (formatChar != null) {
            if (!options.areValidFor(formatChar)) {
                throw ParseException.withBounds("invalid format specifier", message, termStart,
                                                termEnd);
            }
            parameter = SimpleParameter.of(index, formatChar, options);
        } else if (typeChar == 't' || typeChar == 'T') {
            if (!options.validate(FLAG_LEFT_ALIGN | FLAG_UPPER_CASE, false)) {
                throw ParseException.withBounds(
                        "invalid format specification", message, termStart, termEnd);
            }
            // Time/date format terms have an extra character in them.
            termEnd += 1;
            if (termEnd > message.length()) {
                throw ParseException.atPosition("truncated format specifier", message, termStart);
            }
            var dateTimeFormat = DateTimeFormat.of(message.charAt(formatStart + 1));
            if (dateTimeFormat == null) {
                throw ParseException.atPosition("illegal date/time conversion", message,
                                                formatStart + 1);
            }
            parameter = DateTimeParameter.of(dateTimeFormat, options, index);
        } else if (typeChar == 'h' || typeChar == 'H') {
            // %h/%H is a legacy format we want to support for syntax compliance with String.format()
            // but which we don't need to support in the backend.
            if (!options.validate(FLAG_LEFT_ALIGN | FLAG_UPPER_CASE, false)) {
                throw ParseException.withBounds(
                        "invalid format specification", message, termStart, termEnd);
            }
            parameter = wrapHexParameter(options, index);
        } else {
            throw ParseException.withBounds(
                    "invalid format specification", message, termStart, formatStart + 1);
        }
        builder.addParameter(termStart, termEnd, parameter);
        return termEnd;
    }

    // Static method so the anonymous synthetic parameter is static, rather than an inner class.
    private static Parameter wrapHexParameter(final FormatOptions options, int index) {
        // %h / %H is really just %x / %X on the hashcode.
        return new Parameter(options, index) {
            @Override
            protected void accept(ParameterVisitor visitor, Object value) {
                visitor.visit(value.hashCode(), FormatChar.HEX, getFormatOptions());
            }

            @Override
            public String getFormat() {
                return options.shouldUpperCase() ? "%H" : "%h";
            }
        };
    }
}
