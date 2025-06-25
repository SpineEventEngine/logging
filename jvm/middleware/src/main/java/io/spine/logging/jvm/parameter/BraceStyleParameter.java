/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.parameter;

import io.spine.logging.jvm.backend.FormatChar;
import io.spine.logging.jvm.backend.FormatOptions;
import io.spine.logging.jvm.backend.FormatType;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * A parameter implementation to mimic the formatting of brace style placeholders (ie, "{n}").
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/BraceStyleParameter.java">
 *         Original Java code of Google Flogger</a>
 */
public class BraceStyleParameter extends Parameter {

    // Format options to mimic how '{0}' is formatted for numbers (i.e. like "%,d" or "%,f").
    private static final FormatOptions WITH_GROUPING =
            FormatOptions.of(FormatOptions.FLAG_SHOW_GROUPING, FormatOptions.UNSET,
                             FormatOptions.UNSET);

    // Message formatter for fallback cases where '{n}' formats sufficiently differently to any
    // available printf specifier that we must preformat the result ourselves.
    // TODO: Get the Locale from the Platform class for better i18n support.
    private static final MessageFormat prototypeMessageFormatter =
            new MessageFormat("{0}", Locale.ROOT);

    /** Cache parameters with indices 0-9 to cover the vast majority of cases. */
    private static final int MAX_CACHED_PARAMETERS = 10;

    /** Map of the most common default general parameters (corresponds to %s, %d, %f etc...). */
    private static final BraceStyleParameter[] DEFAULT_PARAMETERS;

    static {
        DEFAULT_PARAMETERS = new BraceStyleParameter[MAX_CACHED_PARAMETERS];
        for (var index = 0; index < MAX_CACHED_PARAMETERS; index++) {
            DEFAULT_PARAMETERS[index] = new BraceStyleParameter(index);
        }
    }

    /**
     * Returns a {@link Parameter} representing a plain "brace style" placeholder "{n}".
     * Note that a cached value may be returned.
     *
     * @param index
     *         the index of the argument to be processed.
     * @return the immutable, thread safe parameter instance.
     */
    public static BraceStyleParameter of(int index) {
        return index < MAX_CACHED_PARAMETERS
               ? DEFAULT_PARAMETERS[index]
               : new BraceStyleParameter(index);
    }

    private BraceStyleParameter(int index) {
        super(FormatOptions.getDefault(), index);
    }

    @Override
    protected void accept(ParameterVisitor visitor, Object value) {
        // Special cases which MessageFormat treats specially (oddly Calendar is not a special case).
        if (FormatType.INTEGRAL.canFormat(value)) {
            visitor.visit(value, FormatChar.DECIMAL, WITH_GROUPING);
        } else if (FormatType.FLOAT.canFormat(value)) {
            // Technically floating point formatting via {0} differs from "%,f", but as "%,f" results in
            // more precision it seems better to mimic "%,f" rather than discard both precision and type
            // information by calling visitPreformatted().
            visitor.visit(value, FormatChar.FLOAT, WITH_GROUPING);
        } else if (value instanceof Date) {
            // MessageFormat is not thread safe, so we always clone().
            var formatted = ((MessageFormat) prototypeMessageFormatter.clone())
                    .format(new Object[]{value}, new StringBuffer(), null /* field position */)
                    .toString();
            visitor.visitPreformatted(value, formatted);
        } else if (value instanceof Calendar) {
            visitor.visitDateTime(value, DateTimeFormat.DATETIME_FULL, getFormatOptions());
        } else {
            visitor.visit(value, FormatChar.STRING, getFormatOptions());
        }
    }

    @Override
    public String getFormat() {
        return "%s";
    }
}
