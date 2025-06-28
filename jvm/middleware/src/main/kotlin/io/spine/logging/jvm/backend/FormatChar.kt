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

package io.spine.logging.jvm.backend

/**
 * An enum representing the `printf`-like formatting characters that must
 * be supported by all logging backends.
 *
 * It is important to note that while backends must accept any of these format
 * specifiers, they are not obliged to implement all specified formatting behavior.
 *
 * The default term formatter takes care of supporting all these options when
 * expressed in their normal `'%X'` form (including flags, width and precision).
 *
 * Custom messages parsers must convert arguments into one of these forms before
 * passing then through to the backend.
 *
 * @property char The lower-case `printf` style formatting character.
 *   **Note** that as this enumeration is not a subset of any other common formatting syntax,
 *   it is unsafe to assume that this character can be used to construct a formatting
 *   string to pass to other formatting libraries.

 * @property type The general format type for this character.
 *
 * @param allowedFlagChars The [flags][allowedFlags] allowed for the [char].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/FormatChar.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public enum class FormatChar(
    public val char: Char,
    public val type: FormatType,
    allowedFlagChars: String,
    hasUpperCaseVariant: Boolean
) {
    /**
     * Formats the argument in a manner specific to the chosen logging backend.
     * 
     * In many cases this will be equivalent to using [STRING], but it allows backend
     * implementations to log more structured representations of known types.
     *
     * This is a non-numeric format with an upper-case variant.
     */
    STRING('s', FormatType.GENERAL, "-#", hasUpperCaseVariant = true),

    /**
     * Formats the argument as a boolean.
     *
     * This is a non-numeric format with an upper-case variant.
     */
    BOOLEAN('b', FormatType.BOOLEAN, "-", hasUpperCaseVariant = true),

    /**
     * Formats a Unicode code-point.
     * 
     * This formatting rule can be applied to any character or integral numeric value,
     * providing that [Character.isValidCodePoint] returns true.
     * 
     * Note that if the argument cannot be represented losslessly as an integer,
     * it must be considered invalid.
     *
     * This is a non-numeric format with an upper-case variant.
     */
    CHAR('c', FormatType.CHARACTER, "-", hasUpperCaseVariant = true),

    /**
     * Formats the argument as a decimal integer.
     *
     * This is a numeric format.
     */
    DECIMAL('d', FormatType.INTEGRAL, "-0+ ,(", hasUpperCaseVariant = false),

    /**
     * Formats the argument as an unsigned octal integer.
     *
     * This is a numeric format.
     *
     * `(` is only supported for `[java.math.BigInteger]` or `[java.math.BigDecimal]`
     */
    OCTAL('o', FormatType.INTEGRAL, "-#0(", hasUpperCaseVariant = false),

    /**
     * Formats the argument as an unsigned hexadecimal integer.
     *
     * This is a numeric format with an upper-case variant.
     *
     * `(` is only supported for `[java.math.BigInteger]` or `[java.math.BigDecimal]`
     */
    HEX('x', FormatType.INTEGRAL, "-#0(", hasUpperCaseVariant = true),

    /**
     * Formats the argument as a signed decimal floating value.
     *
     * This is a numeric format.
     */
    FLOAT('f', FormatType.FLOAT, "-#0+ ,(", hasUpperCaseVariant = false),

    /**
     * Formats the argument using computerized scientific notation.
     *
     * This is a numeric format with an upper-case variant.
     */
    EXPONENT('e', FormatType.FLOAT, "-#0+ (", hasUpperCaseVariant = true),

    /**
     * Formats the argument using general scientific notation.
     *
     * This is a numeric format with an upper-case variant.
     */
    GENERAL('g', FormatType.FLOAT, "-0+ ,(", hasUpperCaseVariant = true),

    /**
     * Formats the argument using hexadecimal exponential form. 
     *
     * This formatting option is primarily useful when debugging issues with the precise
     * bit-wise representation of doubles because no rounding of the value takes place.
     *
     * This is a numeric format with an upper-case variant.
     * 
     * **Note:** This could be optimized with `Double.toHexString()` but this parameter
     * is hardly ever used.
     */
    EXPONENT_HEX('a', FormatType.FLOAT, "-#0+ ", hasUpperCaseVariant = true);

    /**
     * The flags parsed from the [allowedFlagChars].
     *
     * This is package private to hide the precise implementation of how we parse and
     * manage formatting options.
     */
    @get:JvmName("getAllowedFlags") // Keep until we convert the whole package to Kotlin.
    internal val allowedFlags: Int =
        FormatOptions.parseValidFlags(allowedFlagChars, hasUpperCaseVariant)

    /**
     * Obtains the format string for this character according to `printf` conventions.
     */
    public val defaultFormatString: String = "%$char"

    private val hasUpperCaseVariant: Boolean =
        (allowedFlags and FormatOptions.FLAG_UPPER_CASE) != 0

    public companion object {
        
        /**
         *  A direct mapping from character offset to [FormatChar] instance.
         *  
         * Have all 26 letters accounted for because we know that the caller has already
         * checked that this is an ASCII letter. This mapping needs to be fast as it
         * is called for every argument in every log message.
         */
        private val MAP = arrayOfNulls<FormatChar>(26)

        init {
            for (fc in entries) {
                MAP[fc.char.letterIndex] = fc
            }
        }

        /**
         * Returns the numeric index [0-25] of a given ASCII letter (upper or lower case).
         * 
         * If the given value is not an ASCII letter, the returned value is not in the range 0-25.
         */
        private val Char.letterIndex: Int
            get() = (code or 0x20) - 'a'.code

        /**
         *  Returns whether a given ASCII letter is lower case.
         */
        private val Char.isAsciiLowerCase: Boolean
            get() = (code and 0x20) != 0

        /**
         * Obtains [FormatChar] instance corresponding to this character or `null` if
         * there is no such an entry in the [MAP].
         */
        @Suppress("MemberNameEqualsClassName") // OK for this private extension logic.
        private fun Char.toFormatChar(): FormatChar? = MAP[letterIndex]

        /**
         * Returns the FormatChar instance associated with the given printf format specifier.
         * If the given character is not an ASCII letter, a runtime exception is thrown.
         */
        @JvmStatic
        public fun of(c: Char): FormatChar? {
            // Get from the map by converting the char to lower-case
            // (which is the most common case by far).
            // If the given value was not an ASCII letter, then the index will be out-of-range,
            // but when called by the parser, it is always guaranteed to be an ASCII letter
            // (but perhaps not a valid format character).
            val fc = c.toFormatChar()
            if (c.isAsciiLowerCase) {
                // If we were given a lower case char to find,
                // we're done (even if the result is `null`).
                return fc
            }
            // Otherwise handle the case where we found a lower-case format char
            // but no upper-case one.
            return if (fc != null && fc.hasUpperCaseVariant) fc else null
        }
    }
}
