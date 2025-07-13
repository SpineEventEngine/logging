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

import com.google.common.annotations.VisibleForTesting
import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions

/**
 * A simple, single argument, parameter which can format arguments according
 * to the rules specified by [FormatChar].
 *
 * This class is immutable and thread-safe, as per the [Parameter] contract.
 *
 * @property formatChar The basic formatting type.
 * @param index The index of the argument to be processed.
 * @param options Additional formatting options.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/SimpleParameter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
@Immutable
@ThreadSafe
public class SimpleParameter private constructor(
    private val formatChar: FormatChar,
    options: FormatOptions,
    index: Int
) : Parameter(options, index) {

    private val formatString: String = if (formatOptions.isDefault) {
        formatChar.defaultFormatString
    } else buildFormatString(formatOptions, formatChar)

    override fun accept(visitor: ArgumentVisitor, value: Any) {
        visitor.visit(value, formatChar, formatOptions)
    }

    override val format: String = formatString

    public companion object {

        /**
         * Cache parameters with indexes 0-9 to cover the vast majority of cases.
         */
        private const val MAX_CACHED_PARAMETERS = 10

        /**
         * The map of the most common default general parameters
         * like `%s`, `%d`, `%f`, etc.
         */
        private val defaultParameters: Map<FormatChar, Array<SimpleParameter>>

        init {
            val map = mutableMapOf<FormatChar, Array<SimpleParameter>>()
            for (fc in FormatChar.entries) {
                map[fc] = createParameterArray(fc)
            }
            defaultParameters = map
        }

        private fun createParameterArray(formatChar: FormatChar): Array<SimpleParameter> =
            Array(MAX_CACHED_PARAMETERS) {
                SimpleParameter(formatChar, FormatOptions.getDefault(), it)
            }

        /**
         * Returns a [Parameter] representing the given formatting options of the specified
         * formatting character.
         *
         * Note that a cached value may be returned.
         *
         * @param formatChar The basic formatting type.
         * @param index The index of the argument to be processed.
         * @param options Additional formatting options.
         */
        @JvmStatic
        public fun of(formatChar: FormatChar, options: FormatOptions, index: Int): Parameter {
            // We can safely test FormatSpec with '==' because the factory methods always return
            // the default instance if applicable (and the class has no visible constructors).
            return if (index < MAX_CACHED_PARAMETERS && options.isDefault) {
                defaultParameters[formatChar]!![index]
            } else {
                SimpleParameter(formatChar, options, index)
            }
        }

        @JvmStatic
        @VisibleForTesting
        @Suppress("MagicNumber")
        internal fun buildFormatString(options: FormatOptions, formatChar: FormatChar): String {
            // The format char is guaranteed to be a lower-case ASCII character,
            // so can be made upper case by simply subtracting 0x20 (or clearing the 6th bit).
            var c = formatChar.char
            if (options.shouldUpperCase()) {
                c = (c.code and 0xDF).toChar()
            }
            return options.appendPrintfOptions(StringBuilder("%")).append(c).toString()
        }
    }
}
