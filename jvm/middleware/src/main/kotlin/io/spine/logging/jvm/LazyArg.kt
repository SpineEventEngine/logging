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

package io.spine.logging.jvm

import org.jspecify.annotations.Nullable

/**
 * A functional interface for allowing lazily evaluated arguments to be supplied to a logger.
 *
 * This allows callers to defer argument evaluation efficiently when:
 * - Doing "fine" logging that's normally disabled
 * - Applying rate limiting to log statements for better performance control.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LazyArg.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public fun interface LazyArg<T> {

    /**
     * Computes a value to use as a log argument.
     *
     * This method is invoked once the Logging library has determined that logging will
     * occur, and the returned value is used in place of the `LazyArg` instance that was
     * passed into the log statement.
     */
    public fun evaluate(): @Nullable T

    public companion object {

        /**
         * Coerces a lambda expression or method reference to return a lazily evaluated
         * logging argument.
         *
         * Pass in a compatible, no-argument, lambda expression or method reference to have
         * it evaluated only when logging will actually occur.
         *
         * ```kotlin
         * logger.atFine().log("value=%s", lazy { doExpensive() })
         * logger.atWarning().atMostEvery(5, MINUTES).log("value=%s", lazy(stats::create))
         * ```
         *
         * Evaluation of lazy arguments occurs at most once, and always in the same thread
         * from which the logging call was made.
         *
         * Note also that it is almost never suitable to make a `toString()` call "lazy"
         * using this mechanism and, in general, explicitly calling `toString()` on arguments
         * which are being logged is an error as it precludes the ability to log an argument
         * structurally.
         *
         * ### Implementation note
         *
         * This method is essentially a coercing cast for the functional interface to give
         * the compiler a target type to convert a lambda expression or method reference into.
         */
        @JvmStatic
        public fun <T> lazy(lambdaOrMethodReference: LazyArg<T>): LazyArg<T> =
            lambdaOrMethodReference
    }
}
