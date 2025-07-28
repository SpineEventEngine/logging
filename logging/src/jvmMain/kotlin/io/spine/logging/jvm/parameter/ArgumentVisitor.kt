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

/**
 * A visitor of log message arguments, dispatched by {@code Parameter} instances.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/ParameterVisitor.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public interface ArgumentVisitor {

    /**
     * Visits a log message argument with formatting specified by `%s`, `%d` etc.
     *
     * Note that this method may still visit arguments which represent date/time values if
     * the format is not explicit (e.g. `log("time=%s", dateTime)`).
     *
     * @param value The non-null log message argument.
     * @param format The printf format specifier.
     * @param options formatting options.
     */
    public fun visit(value: Any, format: FormatChar, options: FormatOptions)

    /**
     * Visits a date/time log message argument with formatting specified by `%t` or similar.
     *
     * Note that because this method is called based on the specified format (and not
     * the argument type) it may visit arguments whose type is not a known date/time value.
     * This is necessary to permit new date/time types to be supported by different logging
     * backends (e.g., JodaTime).
     *
     * @param value The non-null log message argument.
     * @param format The date/time format specifier.
     * @param options Formatting options.
     */
    public fun visitDateTime(value: Any, format: DateTimeFormat, options: FormatOptions)

    /**
     * Visits a log message argument for which formatting has already occurred.
     *
     * This function is only invoked when non-`printf` message formatting is used
     * (e.g., brace style formatting).
     *
     * The function is intended for use by [Parameter] implementations which describe formatting
     * rules which cannot by represented by either [FormatChar] or [DateTimeFormat].
     * This method discards formatting and type information, and the visitor implementation may
     * choose to reexamine the type of the original argument if doing structural logging.
     *
     * @param value The original non-null log message argument.
     * @param formatted The formatted representation of the argument
     */
    public fun visitPreformatted(value: Any, formatted: String)

    /**
     * Visits a missing argument.
     *
     * This method is called when there is no corresponding value for
     * the parameter's argument index.
     */
    public fun visitMissing()

    /**
     * Visits a null argument.
     */
    public fun visitNull()
}
