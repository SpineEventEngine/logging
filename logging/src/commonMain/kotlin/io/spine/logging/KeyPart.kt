/*
 * Copyright 2020, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging

/**
 * A part of a log site key which has singleton semantics per logging scope.
 *
 * This class is responsible for managing the lifecycle and cleanup of log site keys.
 * It ensures that key references are properly maintained and cleaned up when no longer needed.
 *
 * @see <a href="https://github.com/google/flogger/blob/2c7d806b08217993fea229d833a6f8b748591b45/api/src/main/java/com/google/common/flogger/LoggingScope.java#L143">
 *     Original Flogger code</a> for historic reference.
 */
public class KeyPart private constructor(scope: LoggingScope) {

    private val ref = WeakRef(scope)

    //TODO:2025-09-14:alexander.yevsyukov: Guard for concurrency access.
    private val onCloseHooks = mutableListOf<() -> Unit>()

    public fun addOnCloseHook(hook: () -> Unit) {
        onCloseHooks += hook
    }

    // If this were ever too "bursty" due to removal of many keys for the same scope,
    // we could modify this code to process only a maximum number of removals each time
    // and keep a single "in progress" `KeyPart` around until the next time.
    public fun close() {
        // This executes once for each map entry created in the enclosing scope.
        // It is very dependent on logging usage in the scope and theoretically unbounded.
        val it = onCloseHooks.iterator()
        while (it.hasNext()) {
            it.next().invoke()
            it.remove()
        }
    }

    public companion object {

        //TODO:2025-09-14:alexander.yevsyukov: Guard for concurrency access.
        private val registry = mutableSetOf<KeyPart>()

        public fun create(scope: LoggingScope): KeyPart =
            KeyPart(scope).also { registry += it }

        public fun removeUnusedKeys() {
            // There are always more specialized keys than entries in the reference queue,
            // so the queue should be empty most of the time we get here.

            val dead = ArrayList<KeyPart>()
            for (k in registry) {
                if (k.ref.get() == null) dead += k
            }
            for (k in dead) {
                k.close()
                registry.remove(k)
            }
        }
    }
}
