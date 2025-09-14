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

package io.spine.logging.util

import io.spine.annotation.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A platform-neutral API for tracking recursion depth of logging operations.
 *
 * The current recursion depth is stored as a [CoroutineContext.Element] which makes it
 * coroutine-friendly while remaining platform-neutral.
 *
 * Use `Platform.getCurrentRecursionDepth()` to query the current depth from the outside
 * of the core logging internals.
 *
 * API Note: This class is an internal detail and must not be used outside the core of
 * the Logging library. Backends which need to know the recursion depth should call
 * `io.spine.logging.backend.Platform.getCurrentRecursionDepth()`.
 */
public class RecursionDepth private constructor() : CoroutineContext.Element, AutoCloseable {

    override val key: CoroutineContext.Key<*> get() = Key

    private var value: Int = 0

    /**
     * Do not call this method directly, use `Platform.getCurrentRecursionDepth()`.
     */
    @Internal
    public fun getValue(): Int = value

    override fun close() {
        if (value > 0) {
            value -= 1
            if (value == 0) {
                // Remove the element from the current context when it reaches zero.
                CurrentContext.set(CurrentContext.get().minusKey(Key))
            }
            return
        }
        throw AssertionError(
            "Mismatched calls to `RecursionDepth` (possible error in core library)."
        )
    }

    /**
     * The [CoroutineContext.Key] for managing [RecursionDepth] in a [CoroutineContext].
     */
    public companion object Key : CoroutineContext.Key<RecursionDepth> {

        /**
         *  Holds the current coroutine context for logging operations.
         *
         * The recursion depth itself is stored inside the context element, not in this holder.
         */
        private object CurrentContext {
            private var holder: CoroutineContext = EmptyCoroutineContext
            fun get(): CoroutineContext = holder
            fun set(ctx: CoroutineContext) {
                holder = ctx
            }
        }

        /**
         * Do not call this method directly, use `Platform.getCurrentRecursionDepth()`.
         */
        @Internal
        @JvmStatic
        public fun getCurrentDepth(): Int = CurrentContext.get()[Key]?.value ?: 0

        /**
         * Do not call this method directly, use `Platform.getCurrentRecursionDepth()`.
         */
        @Internal
        @JvmStatic
        public fun enterLogStatement(): RecursionDepth {
            val ctx = CurrentContext.get()
            val depth = ctx[Key] ?: RecursionDepth()
            depth.value += 1
            if (depth.value == 0) {
                error("Negative `RecursionDepth` (-1) encountered.")
            }
            CurrentContext.set(ctx + depth)
            return depth
        }
    }
}
