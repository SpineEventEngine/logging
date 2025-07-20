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

@file:JvmName("StringBuilders")

package io.spine.logging.jvm.backend

import io.spine.logging.jvm.JvmLogSite
import java.io.IOException
import java.math.BigInteger
import java.util.*
import java.util.FormattableFlags.ALTERNATE
import java.util.FormattableFlags.LEFT_JUSTIFY
import java.util.FormattableFlags.UPPERCASE

/**
 * Extension functions for [StringBuilder] related to log message formatting.
 */

/**
 * Appends log-site information in the default format, including a trailing space.
 *
 * @param logSite The log site to be appended (ignored if [JvmLogSite.invalid]).
 * @return whether the log-site was appended.
 */
public fun StringBuilder.appendLogSite(logSite: JvmLogSite): Boolean {
    if (logSite == JvmLogSite.invalid) {
        return false
    }
    this.append(logSite.getClassName())
        .append('.')
        .append(logSite.getMethodName())
        .append(':')
        .append(logSite.getLineNumber())
    return true
}

/**
 * Appends a string representation of the user supplied [Formattable], accounting for any
 * possible runtime exceptions.
 *
 * @param value The value to be formatted.
 * @param options the format options (extracted from a printf placeholder in the log message).
 */
@Suppress("TooGenericExceptionCaught")
public fun StringBuilder.safeFormatTo(value: Formattable, options: FormatOptions) {
    // Only care about 3 specific flags for Formattable.
    var formatFlags =
        options.flags and (FormatOptions.FLAG_LEFT_ALIGN
                or FormatOptions.FLAG_UPPER_CASE
                or FormatOptions.FLAG_SHOW_ALT_FORM)
    if (formatFlags != 0) {
        // TODO: Re-order the options flags to make this step easier or use a lookup table?
        // Note that reordering flags would require a rethink of how they are parsed.
        formatFlags =
            (if (formatFlags and FormatOptions.FLAG_LEFT_ALIGN != 0) LEFT_JUSTIFY else 0) or
                    (if (formatFlags and FormatOptions.FLAG_UPPER_CASE != 0) UPPERCASE else 0) or
                    (if (formatFlags and FormatOptions.FLAG_SHOW_ALT_FORM != 0) ALTERNATE else 0)
    }
    // We may need to undo an arbitrary amount of appending if there is an error.
    val originalLength = this.length
    val formatter = Formatter(this, FORMAT_LOCALE)
    try {
        value.formatTo(formatter, formatFlags, options.width, options.precision)
    } catch (e: RuntimeException) {
        // Roll back any partial changes on error and
        // instead append an error string for the value.
        this.setLength(originalLength)
        // We only use a StringBuilder to create the Formatter instance, which never throws.
        try {
            formatter.out().append(formatErrorMessageFor(value, e))
        } catch (_: IOException) {
            /* impossible */
        }
    }
}

/**
 * Appends a hexadecimal representation of the given number.
 *
 * @param number The number to be formatted.
 * @param options the format options.
 */
@Suppress("MagicNumber")
public fun StringBuilder.appendHex(number: Number, options: FormatOptions) {
    // We know there are no unexpected formatting flags
    // (currently only upper-casing is supported).
    val isUpper = options.shouldUpperCase()
    // We cannot just call Long.toHexString() as that would get negative values wrong.
    val n = number.toLong()
    // Roughly, in order of expected usage.
    when (number) {
        is Long -> this.appendHex(n, isUpper)
        is Int -> this.appendHex(n and 0xFFFFFFFFL, isUpper)
        is Byte -> this.appendHex(n and 0xFFL, isUpper)
        is Short -> this.appendHex(n and 0xFFFFL, isUpper)
        is BigInteger -> {
            val hex = number.toString(16)
            this.append(if (isUpper) hex.uppercase(FORMAT_LOCALE) else hex)
        }

        else -> {
            // This will be caught and handled by the logger, but it should never happen.
            error("Unsupported number type: `${number::class.simpleName}`")
        }
    }
}

/**
 * Appends a hexadecimal representation of the given long value.
 *
 * @param n The long value to be formatted.
 * @param isUpper Whether to use uppercase letters for hex digits.
 */
@Suppress("MagicNumber")
internal fun StringBuilder.appendHex(n: Long, isUpper: Boolean) {
    if (n == 0L) {
        this.append("0")
    } else {
        val hexChars = if (isUpper) "0123456789ABCDEF" else "0123456789abcdef"
        // Shift with a value in the range 0..60 and count down in steps of 4.
        // You could unroll this into a switch statement, and it might be faster,
        // but it is likely not worth it.
        var shift = (63 - java.lang.Long.numberOfLeadingZeros(n)) and 3.inv()
        while (shift >= 0) {
            this.append(hexChars[((n ushr shift) and 0xF).toInt()])
            shift -= 4
        }
    }
}

/**
 * Formats an error message for a value that caused an exception.
 *
 * @param value The value that caused the exception.
 * @param e The exception that was thrown.
 * @return A formatted error message.
 */
@Suppress("TooGenericExceptionCaught")
private fun formatErrorMessageFor(value: Any?, e: RuntimeException): String {
    val errorMessage = try {
        e.toString()
    } catch (runtimeException: RuntimeException) {
        // Ok, now you're just being silly...
        runtimeException.javaClass.simpleName
    }
    return "{${value?.javaClass?.name}@${System.identityHashCode(value)}: $errorMessage}"
}
