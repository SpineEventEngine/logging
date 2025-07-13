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

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.jvm.backend.FormatOptions

/**
 * A parameter for formatting date/time arguments.
 *
 * This class is immutable and thread-safe, as per the [Parameter] contract.
 *
 * @property specifier The specifier for the specific date/time formatting to be applied.
 * @param options The validated formatting options.
 * @param index The argument index.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/DateTimeParameter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
@Immutable
@ThreadSafe
public class DateTimeParameter private constructor(
    private val specifier: DateTimeFormat,
    index: Int,
    options: FormatOptions
) : Parameter(options, index) {

    private val formatString: String = buildString {
        append('%')
        formatOptions.appendPrintfOptions(this)
        append(if (formatOptions.shouldUpperCase()) 'T' else 't')
        append(specifier.char)
    }

    override fun accept(visitor: ArgumentVisitor, value: Any) {
        visitor.visitDateTime(value, specifier, formatOptions)
    }

    override val format: String = formatString

    public companion object {

        /**
         * Returns a [Parameter] representing the given formatting options of the specified
         * date/time formatting character.
         *
         * Note that a cached value may be returned.
         *
         * @param format The specifier for the specific date/time formatting to be applied.
         * @param options The validated formatting options.
         * @param index The argument index.
         * @return the immutable, thread-safe parameter instance.
         */
        @JvmStatic
        public fun of(format: DateTimeFormat, options: FormatOptions, index: Int): Parameter =
            DateTimeParameter(format, index, options)
    }
}
