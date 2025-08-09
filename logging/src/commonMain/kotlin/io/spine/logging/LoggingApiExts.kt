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

package io.spine.logging

/**
 * Extension function to log the given message.
 */
public fun <API : LoggingApi<API>> LoggingApi<API>.log(msg: String?) {
    log({ msg })
}

/**
 * Extension function for logging a formatted message with a single argument.
 */
public fun <API : LoggingApi<API>> LoggingApi<API>.log(message: String, arg: Any?) {
    log { String.format(message, arg) }
}

/**
 * Extension function for logging a formatted message with two arguments.
 */
public fun <API : LoggingApi<API>> LoggingApi<API>.log(
    message: String,
    arg1: Any?,
    arg2: Any?
) {
    log { String.format(message, arg1, arg2) }
}

/**
 * Extension function for logging a formatted message with three arguments.
 */
public fun <API : LoggingApi<API>> LoggingApi<API>.log(
    message: String,
    arg1: Any?,
    arg2: Any?,
    arg3: Any?
) {
    log { String.format(message, arg1, arg2, arg3) }
}

/**
 * Extension function for logging a formatted message with multiple arguments.
 */
public fun <API : LoggingApi<API>> LoggingApi<API>.logVarargs(message: String, vararg args: Any?) {
    log { String.format(message, *args) }
}
