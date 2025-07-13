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

package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.backend.FormatType
import java.text.MessageFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A parameter implementation to mimic the formatting of brace style placeholders (i.e., `"{n}"`).
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/BraceStyleParameter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public class BraceStyleParameter internal constructor(index: Int) :
    Parameter(FormatOptions.getDefault(), index) {

    override fun accept(visitor: ArgumentVisitor, value: Any) {
        // Special cases which MessageFormat treats specially
        // (oddly, `Calendar` is not a special case).
        when {
            FormatType.INTEGRAL.canFormat(value) ->
                visitor.visit(value, FormatChar.DECIMAL, withGroupings)
            FormatType.FLOAT.canFormat(value) ->
                // Technically floating point formatting via {0} differs from "%,f", but as "%,f"
                // results in more precision it seems better to mimic "%,f" rather than discard
                // both precision and type information by calling visitPreformatted().
                visitor.visit(value, FormatChar.FLOAT, withGroupings)
            value is Date -> {
                // MessageFormat is not thread safe, so we always `clone()`.
                val formatted = (prototypeMessageFormatter.clone() as MessageFormat)
                    .format(arrayOf(value), StringBuffer(), null)
                    .toString()
                visitor.visitPreformatted(value, formatted)
            }
            value is Calendar ->
                visitor.visitDateTime(value, DateTimeFormat.DATETIME_FULL, formatOptions)
            else ->
                visitor.visit(value, FormatChar.STRING, formatOptions)
        }
    }

    override val format: String = "%s"

    public companion object {

        /**
         * Cache parameters with indexes 0-9 to cover the vast majority of cases.
         */
        private const val MAX_CACHED_PARAMETERS = 10

        /**
         * Map of the most common default general parameters (corresponds to `%s`, `%d`, `%f` etc.).
         */
        private val defaultParameters = Array(MAX_CACHED_PARAMETERS) { BraceStyleParameter(it) }

        /**
         * Format options to mimic how '{0}' is formatted for numbers
         * (i.e., like `"%,d"` or `"%,f"`).
         */
        private val withGroupings = FormatOptions.of(
            FormatOptions.FLAG_SHOW_GROUPING,
            FormatOptions.UNSET,
            FormatOptions.UNSET
        )

        /**
         * Message formatter for fallback cases where `{n}` formats sufficiently differently to any
         * available `printf` specifier that we must preformat the result ourselves.
         */
        // TODO: Get the Locale from the Platform class for better i18n support.
        private val prototypeMessageFormatter = MessageFormat("{0}", Locale.ROOT)


        /**
         * Returns a [Parameter] representing a plain "brace style" placeholder `"{n}"`.
         *
         * Note that a cached value may be returned.
         *
         * @param index The index of the argument to be processed.
         * @return the immutable, thread safe parameter instance.
         */
        @JvmStatic
        public fun of(index: Int): BraceStyleParameter =
            if (index < MAX_CACHED_PARAMETERS) {
                defaultParameters[index]
            } else {
                BraceStyleParameter(index)
            }
    }
}
