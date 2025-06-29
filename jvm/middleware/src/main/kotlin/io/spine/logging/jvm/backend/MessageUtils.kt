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

import io.spine.logging.jvm.JvmLogSite
import io.spine.logging.jvm.backend.FormatOptions.Companion.FLAG_LEFT_ALIGN
import io.spine.logging.jvm.backend.FormatOptions.Companion.FLAG_SHOW_ALT_FORM
import io.spine.logging.jvm.backend.FormatOptions.Companion.FLAG_UPPER_CASE
import java.io.IOException
import java.math.BigInteger
import java.util.*
import java.util.FormattableFlags.ALTERNATE
import java.util.FormattableFlags.LEFT_JUSTIFY
import java.util.FormattableFlags.UPPERCASE

/**
 * Static utilities for classes wishing to implement their own log message formatting. None of the
 * methods here are required in a formatter, but they should help solve problems common to log
 * message formatting.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MessageUtils.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public object MessageUtils {

    /**
     * Error message for if toString() returns null.
     */
    private const val NULL_TOSTRING_MESSAGE = "toString() returned null"

    /**
     * The locale to be used for the formatting.
     *
     * It would be more "proper" to use `Locale.getDefault(Locale.Category.FORMAT)` here,
     * but also removes the capability of optimising certain formatting operations.
     */
    @JvmField
    public val FORMAT_LOCALE: Locale = Locale.ROOT

    /**
     * Appends log-site information in the default format, including a trailing space.
     *
     * @param logSite The log site to be appended (ignored if [JvmLogSite.INVALID]).
     * @param out The destination buffer.
     * @return whether the log-site was appended.
     */
    @JvmStatic
    public fun appendLogSite(logSite: JvmLogSite, out: StringBuilder): Boolean {
        if (logSite == JvmLogSite.INVALID) {
            return false
        }
        out.append(logSite.className)
            .append('.')
            .append(logSite.methodName)
            .append(':')
            .append(logSite.lineNumber)
        return true
    }

    /**
     * Returns a string representation of the user-supplied value
     * accounting for any possible runtime exceptions.
     *
     * This code will never fail but may return a synthetic error
     * string if exceptions were thrown.
     *
     * @param value The value to be formatted.
     * @return a best-effort string representation of the given value,
     *   even if exceptions were thrown.
     */
    @JvmStatic
    @Suppress("TooGenericExceptionCaught")
    public fun safeToString(value: Any?): String {
        return try {
            toNonNullString(value)
        } catch (e: RuntimeException) {
            getErrorString(value, e)
        }
    }

    /**
     * Returns a string representation of the user-supplied value.
     *
     * This method should try hard to return a human-readable representation, possibly
     * going beyond the default [toString] representation for some well-defined types.
     *
     * @param value The value to be formatted (possibly `null`).
     * @return a non-null string representation of the given value (possibly `null`).
     */
    @Suppress("ReturnCount")
    private fun toNonNullString(value: Any?): String {
        if (value == null) {
            return "null"
        }
        if (!value.javaClass.isArray) {
            // toString() itself can return `null` and surprisingly
            // "String.valueOf(value)" doesn't handle
            // that, and we want to ensure we never return "null".
            // We also want to distinguish a null
            // value (which is normal) from having toString() return `null` (which is an error).
            val s: String? = value.toString()
            return s ?: formatErrorMessageFor(value, NULL_TOSTRING_MESSAGE)
        }
        // None of the following methods can return `null` if given a non-null value.
        return when (value) {
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            is ByteArray -> value.contentToString()
            is CharArray -> value.contentToString()
            is ShortArray -> value.contentToString()
            is FloatArray -> value.contentToString()
            is DoubleArray -> value.contentToString()
            is BooleanArray -> value.contentToString()
            // Non fundamental type array.
            is Array<*> -> value.contentToString()
            else -> value.toString()
        }
    }

    /**
     * Returns a string representation of the user supplied [Formattable], accounting for any
     * possible runtime exceptions.
     *
     * @param value The value to be formatted.
     * @param out The buffer into which to format it.
     * @param options the format options (extracted from a printf placeholder in the log message).
     */
    @JvmStatic
    @Suppress("TooGenericExceptionCaught")
    public fun safeFormatTo(value: Formattable, out: StringBuilder, options: FormatOptions) {
        // Only care about 3 specific flags for Formattable.
        var formatFlags =
            options.flags and (FLAG_LEFT_ALIGN or FLAG_UPPER_CASE or FLAG_SHOW_ALT_FORM)
        if (formatFlags != 0) {
            // TODO: Re-order the options flags to make this step easier or use a lookup table?
            // Note that reordering flags would require a rethink of how they are parsed.
            formatFlags =
                (if (formatFlags and FLAG_LEFT_ALIGN != 0) LEFT_JUSTIFY else 0) or
                        (if (formatFlags and FLAG_UPPER_CASE != 0) UPPERCASE else 0) or
                        (if (formatFlags and FLAG_SHOW_ALT_FORM != 0) ALTERNATE else 0)
        }
        // We may need to undo an arbitrary amount of appending if there is an error.
        val originalLength = out.length
        val formatter = Formatter(out, FORMAT_LOCALE)
        try {
            value.formatTo(formatter, formatFlags, options.width, options.precision)
        } catch (e: RuntimeException) {
            // Roll-back any partial changes on error, and
            // instead append an error string for the value.
            out.setLength(originalLength)
            // We only use a StringBuilder to create the Formatter instance, which never throws.
            try {
                formatter.out().append(getErrorString(value, e))
            } catch (_: IOException) {
                /* impossible */
            }
        }
    }

    // Visible for testing
    @JvmStatic
    @Suppress("MagicNumber")
    public fun appendHex(out: StringBuilder, number: Number, options: FormatOptions) {
        // We know there are no unexpected formatting flags
        // (currently only upper-casing is supported).
        val isUpper = options.shouldUpperCase()
        // We cannot just call Long.toHexString() as that would get negative values wrong.
        val n = number.toLong()
        // Roughly, in order of expected usage.
        when (number) {
            is Long -> appendHex(out, n, isUpper)
            is Int -> appendHex(out, n and 0xFFFFFFFFL, isUpper)
            is Byte -> appendHex(out, n and 0xFFL, isUpper)
            is Short -> appendHex(out, n and 0xFFFFL, isUpper)
            is BigInteger -> {
                val hex = number.toString(16)
                out.append(if (isUpper) hex.uppercase(FORMAT_LOCALE) else hex)
            }

            else -> {
                // This will be caught and handled by the logger, but it should never happen.
                error("Unsupported number type: `${number::class.simpleName}`")
            }
        }
    }

    @Suppress("MagicNumber")
    internal fun appendHex(out: StringBuilder, n: Long, isUpper: Boolean) {
        if (n == 0L) {
            out.append("0")
        } else {
            val hexChars = if (isUpper) "0123456789ABCDEF" else "0123456789abcdef"
            // Shift with a value in the range 0..60 and count down in steps of 4.
            // You could unroll this into a switch statement, and it might be faster,
            // but it is likely not worth it.
            var shift = (63 - java.lang.Long.numberOfLeadingZeros(n)) and 3.inv()
            while (shift >= 0) {
                out.append(hexChars[((n ushr shift) and 0xF).toInt()])
                shift -= 4
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun getErrorString(value: Any?, e: RuntimeException): String {
        val errorMessage = try {
            e.toString()
        } catch (runtimeException: RuntimeException) {
            // Ok, now you're just being silly...
            runtimeException.javaClass.simpleName
        }
        return formatErrorMessageFor(value, errorMessage)
    }

    private fun formatErrorMessageFor(value: Any?, errorMessage: String): String {
        return "{${value?.javaClass?.name}@${System.identityHashCode(value)}: $errorMessage}"
    }
}
