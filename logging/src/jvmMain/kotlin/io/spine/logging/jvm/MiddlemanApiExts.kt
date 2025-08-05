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

@file:Suppress("RedundantUnitReturnType")

package io.spine.logging.jvm

/**
 * Extension method to maintain backward compatibility with the previous API.
 *
 * This allows calling `log(String?)` with a string literal, which will be wrapped
 * in a lambda to match the new API signature.
 */
public fun <API : MiddlemanApi<API>> MiddlemanApi<API>.log(msg: String?) =
    log { msg }

/**
 * Extension method for logging a formatted message with a single argument.
 */
public fun <API : MiddlemanApi<API>> MiddlemanApi<API>.log(message: String, arg: Any?) =
    log { String.format(message, arg) }

/**
 * Extension method for logging a formatted message with two arguments.
 */
public fun <API : MiddlemanApi<API>> MiddlemanApi<API>.log(
    message: String,
    arg1: Any?,
    arg2: Any?
) = log { String.format(message, arg1, arg2) }

/**
 * Extension method for logging a formatted message with three arguments.
 */
public fun <API : MiddlemanApi<API>> MiddlemanApi<API>.log(
    message: String,
    arg1: Any?,
    arg2: Any?,
    arg3: Any?
) = log { String.format(message, arg1, arg2, arg3) }

/**
 * Extension method for logging a formatted message with multiple arguments.
 */
public fun <API : MiddlemanApi<API>> MiddlemanApi<API>.log(message: String, vararg args: Any?) =
    log { String.format(message, *args) }

/**
 * Extension method for logging a formatted message with an array of arguments.
 */
public fun <API : MiddlemanApi<API>> MiddlemanApi<API>.logVarargs(
    message: String,
    params: Array<Any?>?
) = log { if (params != null) String.format(message, *params) else message }

