/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.util

import java.io.Closeable

/**
 * A thread local counter, incremented whenever a log statement is being processed by the backend.
 *
 * If this value is greater than 1, then reentrant logging has occurred, and some code may behave
 * differently to avoid issues such as unbounded recursion. Logging may even be disabled completely
 * if the depth gets too high.
 *
 * #### API Note
 * This class is an internal detail and must not be used outside the core of the Logging library.
 * Backends which need to know the recursion depth should call
 * [io.spine.logging.backend.Platform.getCurrentRecursionDepth].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/util/RecursionDepth.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public class RecursionDepth private constructor() : Closeable {

    public companion object {

        private val holder = object : ThreadLocal<RecursionDepth>() {
            override fun initialValue(): RecursionDepth = RecursionDepth()
        }

        /**
         * Do not call this method directly, use `Platform.getCurrentRecursionDepth()`.
         */
        @JvmStatic
        public fun getCurrentDepth(): Int = holder.get().value

        /**
         * Do not call this method directly, use `Platform.getCurrentRecursionDepth()`.
         */
        @JvmStatic
        public fun enterLogStatement(): RecursionDepth {
            val depth = holder.get()
            if (++depth.value == 0) {
                throw AssertionError(
                    "Overflow of RecursionDepth (possible error in core library)"
                )
            }
            return depth
        }
    }

    private var value = 0

    /**
     * Do not call this method directly, use `Platform.getCurrentRecursionDepth()`.
     */
    public fun getValue(): Int = value

    override fun close() {
        if (value > 0) {
            value -= 1
            return
        }
        throw AssertionError("Mismatched calls to RecursionDepth (possible error in core library)")
    }
}
