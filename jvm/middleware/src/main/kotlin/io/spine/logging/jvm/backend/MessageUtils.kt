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
