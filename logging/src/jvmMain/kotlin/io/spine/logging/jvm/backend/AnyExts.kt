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

@file:JvmName("AnyMessages")

package io.spine.logging.jvm.backend

import java.util.*

/**
 * Extension functions for classes wishing to implement their own log message formatting.
 *
 * None of the functions here are required in a formatter, but they should help solve
 * problems common to log message formatting.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MessageUtils.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */

/**
 * Error message for if `toString()` returns null.
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
 * @return a best-effort string representation of the given value,
 *   even if exceptions were thrown.
 */
@Suppress("TooGenericExceptionCaught")
public fun Any?.safeToString(): String {
    return try {
        toNonNullString()
    } catch (e: RuntimeException) {
        getErrorString(e)
    }
}

/**
 * Returns a string representation of the user-supplied value.
 *
 * This method should try hard to return a human-readable representation, possibly
 * going beyond the default [toString] representation for some well-defined types.
 *
 * @return a non-null string representation of the given value (possibly `null`).
 */
@Suppress("ReturnCount")
private fun Any?.toNonNullString(): String {
    if (this == null) {
        return "null"
    }
    if (!this.javaClass.isArray) {
        // toString() itself can return `null` and surprisingly
        // "String.valueOf(value)" doesn't handle
        // that, and we want to ensure we never return "null".
        // We also want to distinguish a null
        // value (which is normal) from having toString() return `null` (which is an error).
        val s: String? = this.toString()
        return s ?: formatErrorMessageFor(NULL_TOSTRING_MESSAGE)
    }
    // None of the following methods can return `null` if given a non-null value.
    return when (this) {
        is IntArray -> this.contentToString()
        is LongArray -> this.contentToString()
        is ByteArray -> this.contentToString()
        is CharArray -> this.contentToString()
        is ShortArray -> this.contentToString()
        is FloatArray -> this.contentToString()
        is DoubleArray -> this.contentToString()
        is BooleanArray -> this.contentToString()
        // Non fundamental type array.
        is Array<*> -> this.contentToString()
        else -> this.toString()
    }
}

@Suppress("TooGenericExceptionCaught")
private fun Any?.getErrorString(e: RuntimeException): String {
    val errorMessage = try {
        e.toString()
    } catch (runtimeException: RuntimeException) {
        // Ok, now you're just being silly...
        runtimeException.javaClass.simpleName
    }
    return formatErrorMessageFor(errorMessage)
}

private fun Any?.formatErrorMessageFor(errorMessage: String): String =
    "{${this?.javaClass?.name}@${System.identityHashCode(this)}: $errorMessage}"
