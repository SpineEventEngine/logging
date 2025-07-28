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

package io.spine.logging.jvm.backend

import java.math.BigDecimal
import java.math.BigInteger

/**
 * The general formatting type of any one of the predefined `FormatChar` instances.
 *
 * @property isNumeric Is `true` if this format type requires a [Number] instance
 *   (or one of the corresponding fundamental types) as an argument.
 * @property supportsPrecision Is `true` if the notion of a specified precision value
 *   makes sense to this format type. Precision is specified in addition to width and
 *   can control the resolution of a formatting operation (e.g., how many digits
 *   are included in the output after the decimal point for floating point values).
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/FormatType.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public enum class FormatType(
    @get:JvmName("isNumeric")
    public val isNumeric: Boolean,

    @get:JvmName("supportsPrecision")
    public val supportsPrecision: Boolean
) {
    /**
     * General formatting that can be applied to any type.
     */
    GENERAL(false, true) {
        override fun canFormat(arg: Any?): Boolean =
            true
    },

    /**
     * Formatting that can be applied to any boolean type.
     */
    BOOLEAN(false, false) {
        override fun canFormat(arg: Any?): Boolean =
            arg is Boolean
    },

    /**
     * Formatting that can be applied to Character or any integral type that can be losslessly
     * converted to an int and for which [Character.isValidCodePoint] returns true.
     */
    CHARACTER(false, false) {
        override fun canFormat(arg: Any?): Boolean =
            // Ordering in a relative likelihood.
            when (arg) {
                is Char -> true
                is Int, is Byte, is Short -> Character.isValidCodePoint((arg as Number).toInt())
                else -> false
            }
    },

    /**
     * Formatting that can be applied to any integral Number type.
     *
     * Logging backends must support `Byte`, `Short`, `Integer`, `Long` and `BigInteger`
     * but may also support additional numeric types directly. A logging backend that
     * encounters an unknown numeric type should fall back to using `toString()`.
     */
    INTEGRAL(true, false) {
        override fun canFormat(arg: Any?): Boolean =
            // Ordering in a relative likelihood.
            arg is Int
                    || arg is Long
                    || arg is Byte
                    || arg is Short
                    || arg is BigInteger
    },

    /**
     * Formatting that can be applied to any `Number` type.
     *
     * Logging backends must support all the integral types as well as
     * `Float`, `Double` and `BigDecimal`, but may also support additional
     * numeric types directly. A logging backend that encounters an unknown
     * numeric type should fall back to using `toString()`.
     */
    FLOAT(true, true) {
        override fun canFormat(arg: Any?): Boolean =
            // Ordering in a relative likelihood.
            arg is Double || arg is Float || arg is BigDecimal
    };

    /**
     * Tells if the given value can be formatted by this type.
     */
    public abstract fun canFormat(arg: Any?): Boolean
}
