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

import io.spine.logging.jvm.parser.ParseException

/**
 * A structured representation of formatting options compatible with printf style formatting.
 *
 * This class is immutable and thread-safe.
 *
 * @property flags The flag bits for this options instance.
 *   Where possible the per-flag methods `shouldXxx()` should be preferred for code clarity,
 *   but for efficiency and when testing multiple flags values at the same time,
 *   this method is useful.
 *
 * @property width The width for these options, or [UNSET] if not specified.
 *   This is a non-negative decimal integer, which typically indicates the minimum
 *   number of characters to be written to the output, but its precise meaning is
 *   dependent on the formatting rule it is applied to.
 *
 * @property precision The precision for these options, or [UNSET] if not specified.
 *   This is a non-negative decimal integer, usually used to restrict the number of characters,
 *   but its precise meaning is dependent on the formatting rule it is applied to.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/FormatOptions.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
@Suppress("TooManyFunctions")
public class FormatOptions private constructor(
    public val flags: Int,
    public val width: Int,
    public val precision: Int
) {

    @Suppress("MagicNumber")
    public companion object {

        private const val MAX_ALLOWED_WIDTH = 999999
        private const val MAX_ALLOWED_PRECISION = 999999

        // WARNING: Never add any more flags here (flag encoding breaks if > 7 flags).
        private const val FLAG_CHARS_ORDERED = " #(+,-0"
        private const val MIN_FLAG_VALUE = ' '.code
        private const val MAX_FLAG_VALUE = '0'.code

        // For a flag character 'c' in [MIN_FLAG_VALUE, MAX_FLAG_VALUE] the flag index
        // is stored in 3 bits starting at bit-N, where N = (3 * (c - MIN_FLAG_VALUE)).
        private val ENCODED_FLAG_INDICES: Long

        public const val INVALID_FLAG: String = "invalid flag"

        init {
            var encoded = 0L
            for (i in FLAG_CHARS_ORDERED.indices) {
                val n = (FLAG_CHARS_ORDERED[i].code - MIN_FLAG_VALUE).toLong()
                encoded = encoded or ((i + 1L) shl (3 * n).toInt())
            }
            ENCODED_FLAG_INDICES = encoded
        }

        /**
         * Helper to decode a flag character which has already been determined to be in the range
         * [MIN_FLAG_VALUE, MAX_FLAG_VALUE]. For characters in this range, this function is
         * identical to `return FLAG_CHARS_ORDERED.indexOf(c)` but without any looping.
         */
        private fun indexOfFlagCharacter(c: Char): Int {
            // TODO: Benchmark against "FLAG_CHARS_ORDERED.indexOf(c)" just to be sure.
            return ((ENCODED_FLAG_INDICES ushr (3 * (c.code - MIN_FLAG_VALUE))) and 0x7L)
                .toInt() - 1
        }

        /**
         * A formatting flag which specifies that for signed numeric output, positive values
         * should be prefixed with an ASCII space (`' '`).
         *
         * This corresponds to the `' '` printf flag and is valid for all signed numeric types.
         */
        public const val FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES: Int = (1 shl 0)

        /**
         * A formatting flag which specifies that output should be shown in
         * a type-dependent alternate form.
         *
         * This corresponds to the '#' printf flag and is valid for:
         * - Octal (%o) and hexadecimal (%x, %X) formatting, where it specifies that
         *   the radix should be shown.
         * - Floating point (%f) and exponential (%e, %E, %a, %A) formatting,
         *   where it specifies that a decimal separator should always be shown.
         */
        public const val FLAG_SHOW_ALT_FORM: Int = (1 shl 1)

        /**
         * A formatting flag which specifies that for signed numeric output,
         * negative values should be surrounded by parentheses.
         *
         * This corresponds to the `'('` printf flag and is valid for all signed numeric types.
         */
        public const val FLAG_USE_PARENS_FOR_NEGATIVE_VALUES: Int = (1 shl 2)

        /**
         * A formatting flag which specifies that for signed numeric output,
         * positive values should be prefixed with an ASCII plus (`'+'`).
         *
         * This corresponds to the '+' printf flag and is valid for all signed numeric types.
         */
        public const val FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES: Int = (1 shl 3)

        /**
         * A formatting flag which specifies that for non-exponential, base-10, numeric output a
         * grouping separator (often a `','`) should be used.
         *
         * This corresponds to the `','` `printf` flag and is valid for:
         * - Decimal (`%d`) and unsigned (`%u`) formatting.
         * - Float (`%f`) and general scientific notation (`%g`, `%G`)
         */
        public const val FLAG_SHOW_GROUPING: Int = (1 shl 4)

        /**
         * A formatting flag which specifies that output should be left-aligned
         * within the minimum available width.
         *
         * This corresponds to the `'-'` printf flag and is valid for all [FormatChar] instances,
         * though it must be specified in conjunction with a width value.
         */
        public const val FLAG_LEFT_ALIGN: Int = (1 shl 5)

        /**
         * A formatting flag which specifies that numeric output should be padding with
         * leading zeros as necessary to fill the minimum width.
         *
         * This corresponds to the `'0'` printf flag and is valid for all numeric types,
         * though it must be specified in conjunction with a width value.
         */
        public const val FLAG_SHOW_LEADING_ZEROS: Int = (1 shl 6)

        /**
         * A formatting flag which specifies that output should be upper-cased after
         * all other formatting.
         *
         * This corresponds to having an upper-case format character and is valid
         * for any type with an upper case variant.
         */
        public const val FLAG_UPPER_CASE: Int = (1 shl 7)

        /**
         * A mask of all allowed formatting flags. Useful when filtering options via [filter].
         */
        public const val ALL_FLAGS: Int = 0xFF

        /**
         * The value used to specify that either width or precision were not specified.
         */
        public const val UNSET: Int = -1

        private val DEFAULT = FormatOptions(0, UNSET, UNSET)

        /**
         * Returns the default options singleton instance.
         */
        @JvmStatic
        public fun getDefault(): FormatOptions = DEFAULT

        /**
         * Creates an instance with the given values.
         */
        @Suppress("MagicNumber", "ReturnCount")
        @JvmStatic
        public fun of(flags: Int, width: Int, precision: Int): FormatOptions {
            require(checkFlagConsistency(flags, width != UNSET)) {
                "invalid flags: 0x${flags.toString(16)}"
            }
            require((width in 1..MAX_ALLOWED_WIDTH) || width == UNSET) {
                "invalid width: $width"
            }
            require((precision in 0..MAX_ALLOWED_PRECISION) || precision == UNSET) {
                "invalid precision: $precision"
            }
            return FormatOptions(flags, width, precision)
        }

        /**
         * Parses a subsequence of a log message to extract and return its options.
         *
         * Note that callers cannot rely on this method producing new instances each time
         * it is called as caching of common option values may occur.
         *
         * @param message The original log message in which the formatting options
         *   have been identified.
         * @param pos The index of the first character to parse.
         * @param end The index after the last character to be parsed.
         * @return the parsed options instance.
         * @throws ParseException if the specified subsequence of the string could not be parsed.
         */
        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "ReturnCount", "ThrowsCount")
        public fun parse(message: String, pos: Int, end: Int, isUpperCase: Boolean): FormatOptions {
            // It is vital that we shortcut parsing and return the default instance here
            // (rather than just creating a new instance with default values) because we
            // check for it using '==' later).
            // Also, it saves us thousands of otherwise unnecessary allocations.
            if (pos == end && !isUpperCase) {
                return DEFAULT
            }

            // STEP 1: Parse flag bits.
            var flags = if (isUpperCase) FLAG_UPPER_CASE else 0
            var currentPos = pos
            var c: Char
            while (true) {
                if (currentPos == end) {
                    return FormatOptions(flags, UNSET, UNSET)
                }
                c = message[currentPos++]
                if (c.code < MIN_FLAG_VALUE || c.code > MAX_FLAG_VALUE) {
                    break
                }
                val flagIdx = indexOfFlagCharacter(c)
                if (flagIdx < 0) {
                    if (c == '.') {
                        // Edge case of something like "%.2f" (precision but no width).
                        return FormatOptions(flags, UNSET, parsePrecision(message, currentPos, end))
                    }
                    throw ParseException.atPosition(INVALID_FLAG, message, currentPos - 1)
                }
                val flagBit = 1 shl flagIdx
                if ((flags and flagBit) != 0) {
                    throw ParseException.atPosition("repeated flag", message, currentPos - 1)
                }
                flags = flags or flagBit
            }

            // STEP 2: Parse width (which must start with [1-9]).
            // We know that c > MAX_FLAG_VALUE, which is really just '0', so (c >= 1)
            val widthStart = currentPos - 1
            if (c > '9') {
                throw ParseException.atPosition(INVALID_FLAG, message, widthStart)
            }
            var width = c.code - '0'.code
            while (true) {
                if (currentPos == end) {
                    return FormatOptions(flags, width, UNSET)
                }
                c = message[currentPos++]
                if (c == '.') {
                    return FormatOptions(flags, width, parsePrecision(message, currentPos, end))
                }
                val n = c.code - '0'.code
                if (n >= 10) {
                    throw ParseException.atPosition(
                        "invalid width character", message, currentPos - 1
                    )
                }
                width = (width * 10) + n
                if (width > MAX_ALLOWED_WIDTH) {
                    throw ParseException.withBounds("width too large", message, widthStart, end)
                }
            }
        }

        @Suppress("ThrowsCount")
        private fun parsePrecision(message: String, start: Int, end: Int): Int {
            if (start == end) {
                throw ParseException.atPosition("missing precision", message, start - 1)
            }
            var precision = 0
            for (pos in start until end) {
                val n = message[pos].code - '0'.code
                if (n >= 10) {
                    throw ParseException.atPosition("invalid precision character", message, pos)
                }
                precision = (precision * 10) + n
                if (precision > MAX_ALLOWED_PRECISION) {
                    throw ParseException.withBounds("precision too large", message, start, end)
                }
            }
            // Check for many-zeros corner case (eg, "%.000f")
            if (precision == 0 && end != (start + 1)) {
                throw ParseException.withBounds("invalid precision", message, start, end)
            }
            return precision
        }

        /** Internal helper method for creating a bit-mask from a string of valid flag characters. */
        @JvmStatic
        @Suppress("UseRequire")
        internal fun parseValidFlags(flagChars: String, hasUpperVariant: Boolean): Int {
            var flags = if (hasUpperVariant) FLAG_UPPER_CASE else 0
            for (i in flagChars.indices) {
                val flagIdx = indexOfFlagCharacter(flagChars[i])
                if (flagIdx < 0) {
                    throw IllegalArgumentException("invalid flags: $flagChars")
                }
                flags = flags or (1 shl flagIdx)
            }
            return flags
        }

        // Helper to check for legal combinations of flags.
        @Suppress("ReturnCount")
        internal fun checkFlagConsistency(flags: Int, hasWidth: Boolean): Boolean {
            // Check that we specify at most one of 'prefix plus' and 'prefix space'.
            if ((flags and
                        (FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES or
                                FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES))
                == (FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES or FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES)
            ) {
                return false
            }
            // Check that we specify at most one of 'left align' and 'leading zeros'.
            if ((flags and (FLAG_LEFT_ALIGN or FLAG_SHOW_LEADING_ZEROS))
                == (FLAG_LEFT_ALIGN or FLAG_SHOW_LEADING_ZEROS)
            ) {
                return false
            }
            // Check that if 'left align' or 'leading zeros' is specified,
            // we also have a width value.
            if ((flags and (FLAG_LEFT_ALIGN or FLAG_SHOW_LEADING_ZEROS)) != 0 && !hasWidth) {
                return false
            }
            return true
        }
    }

    /**
     * Returns a possibly new FormatOptions instance possibly containing a subset of the formatting
     * information. This is useful if a backend implementation wishes to create formatting options
     * that ignore some of the specified formatting information.
     *
     * @param allowedFlags A mask of flag values to be retained in the returned instance. Use
     *         [ALL_FLAGS] to retain all flag values, or `0` to suppress all flags.
     * @param allowWidth specifies whether to include width in the returned instance.
     * @param allowPrecision specifies whether to include precision in the returned instance.
     */
    @Suppress("ReturnCount")
    public fun filter(
        allowedFlags: Int,
        allowWidth: Boolean,
        allowPrecision: Boolean
    ): FormatOptions {
        if (isDefault) {
            return this
        }
        val newFlags = allowedFlags and flags
        val newWidth = if (allowWidth) width else UNSET
        val newPrecision = if (allowPrecision) precision else UNSET
        // Remember that we must never create a non-canonical default instance.
        if (newFlags == 0 && newWidth == UNSET && newPrecision == UNSET) {
            return DEFAULT
        }
        // This check would be faster if we encoded the entire state into a long value.
        // It's also entirely possible we should just allocate a new instance and be damned
        // (especially as having anything other than the default instance is rare).
        // TODO(dbeaumont): Measure performance and see about removing this code,
        //  almost certainly fine.
        if (newFlags == flags && newWidth == width && newPrecision == precision) {
            return this
        }
        return FormatOptions(newFlags, newWidth, newPrecision)
    }

    /**
     * Returns `true` if this instance has only default formatting options.
     */
    @Suppress("ReferenceEquality")
    public val isDefault: Boolean
        get() = this === DEFAULT

    /**
     * Validates these options according to the allowed criteria and checks for inconsistencies in
     * flag values.
     *
     * Note that there is not requirement for options used internally in custom message parsers to
     * be validated, but any format options passed through the `ParameterVisitor` interface must
     * be valid with respect to the associated [FormatChar] instance.
     *
     * @param allowedFlags A bit mask specifying a subset of the printf flags that are allowed for
     *   these options.
     * @param allowPrecision `true` if these options are allowed to have
     *   a precision value specified, `false` otherwise.
     * @return `true` if these options are valid given the specified constraints.
     */
    @Suppress("ReturnCount")
    public fun validate(allowedFlags: Int, allowPrecision: Boolean): Boolean {
        // The default instance is always valid (the commonest case).
        if (isDefault) {
            return true
        }
        // Check if our flags are a subset of the allowed flags.
        if ((flags and allowedFlags.inv()) != 0) {
            return false
        }
        // Check we only have precision specified when it is allowed.
        if (!allowPrecision && precision != UNSET) {
            return false
        }
        return checkFlagConsistency(flags, width != UNSET)
    }

    /**
     * Validates these options as if they were being applied to the given [FormatChar] and
     * checks for inconsistencies in flag values.
     *
     * Note that there is not requirement for options used internally in custom message parsers to
     * be validated, but any format options passed through the
     * `ParameterVisitor` interface must
     * be valid with respect to the associated [FormatChar] instance.
     *
     * @param formatChar the formatting rule to check these options against.
     * @return true if these options are valid for the given format.
     */
    public fun areValidFor(formatChar: FormatChar): Boolean {
        return validate(formatChar.allowedFlags, formatChar.type.supportsPrecision())
    }

    /**
     * Corresponds to `printf` flag '-' (incompatible with '0').
     *
     * Logging backends may ignore this flag, though it does provide some visual
     * clarity when aligning values.
     */
    public fun shouldLeftAlign(): Boolean = (flags and FLAG_LEFT_ALIGN) != 0

    /**
     * Corresponds to printf flag '#'.
     *
     * Logging backends should honor this flag for hex or octal, as it is a common
     * way to avoid ambiguity when formatting non-decimal values.
     */
    public fun shouldShowAltForm(): Boolean = (flags and FLAG_SHOW_ALT_FORM) != 0

    /**
     * Corresponds to printf flag '0'.
     *
     * Logging backends should honor this flag, as it is very commonly used to format hexadecimal or
     * octal values to allow specific bit values to be calculated.
     */
    public fun shouldShowLeadingZeros(): Boolean = (flags and FLAG_SHOW_LEADING_ZEROS) != 0

    /**
     * Corresponds to `printf` flag `'+'`.
     *
     * Logging backends are free to ignore this flag, though it does provide some visual clarity
     * when tabulating certain types of values.
     */
    public fun shouldPrefixPlusForPositiveValues(): Boolean =
        (flags and FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES) != 0

    /**
     * Corresponds to `printf` flag `' '`.
     *
     * Logging backends are free to ignore this flag, though if they choose to support
     * [shouldPrefixPlusForPositiveValues] then it is advisable to support this as well.
     */
    public fun shouldPrefixSpaceForPositiveValues(): Boolean =
        (flags and FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES) != 0

    /**
     * Corresponds to `printf` flag `','`.
     *
     * Logging backends are free to select the locale in which the formatting will occur or ignore
     * this flag altogether.
     */
    public fun shouldShowGrouping(): Boolean = (flags and FLAG_SHOW_GROUPING) != 0

    /**
     * Corresponds to formatting with an upper-case format character.
     *
     * Logging backends are free to ignore this flag.
     */
    public fun shouldUpperCase(): Boolean = (flags and FLAG_UPPER_CASE) != 0

    /**
     * Appends the data for this options instance in a printf compatible form to the given buffer.
     * This method neither appends the leading `'%'` symbol, nor a format type character.
     * Output is written in the form `[width][.precision][flags]` and for the default instance,
     * nothing is appended.
     *
     * @param out The output buffer to which the options are appended.
     */
    public fun appendPrintfOptions(out: StringBuilder): StringBuilder {
        if (isDefault) {
            return out
        }

        // Knock out the upper-case flag because that does not correspond to an options character.
        val optionFlags = flags and FLAG_UPPER_CASE.inv()
        var bit = 0
        while ((1 shl bit) <= optionFlags) {
            if ((optionFlags and (1 shl bit)) != 0) {
                out.append(FLAG_CHARS_ORDERED[bit])
            }
            bit++
        }
        if (width != UNSET) {
            out.append(width)
        }
        if (precision != UNSET) {
            out.append('.').append(precision)
        }
        return out
    }

    override fun equals(other: Any?): Boolean {
        // Various functions ensure that the same instance gets re-used,
        // so it seems likely it is worth optimizing for it here.
        if (other === this) {
            return true
        }
        if (other is FormatOptions) {
            return (other.flags == flags)
                    && (other.width == width)
                    && (other.precision == precision)
        }
        return false
    }

    override fun hashCode(): Int {
        var result = flags
        result = (31 * result) + width
        result = (31 * result) + precision
        return result
    }
}
