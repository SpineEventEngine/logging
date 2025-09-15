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

    /**
     * The concurrency lock for accessing [onCloseHooks].
     */
    private val hookLock = Any()

    /**
     * The list of functions to execute when the instance is [closed][close].
     */
    private val onCloseHooks = mutableListOf<() -> Unit>()

    /**
     * Adds the function to be executed when the instance is [closed][close].
     */
    public fun addOnCloseHook(hook: () -> Unit) {
        synchronized(hookLock) {
            onCloseHooks += hook
        }
    }

    /**
     * Executes clean-up functions previously added by the [addOnCloseHook] functions.
     */
    public fun close() {
        // If this were ever too "bursty" due to removal of many keys for the same scope,
        // we could modify this code to process only a maximum number of removals each time
        // and keep a single "in progress" `KeyPart` around until the next time.

        // This executes once for each map entry created in the enclosing scope.
        // It is very dependent on logging usage in the scope and theoretically unbounded.
        val hooksToRun: List<() -> Unit> = synchronized(hookLock) {
            // Snapshot and clear under lock to avoid concurrent modification.
            val copy = onCloseHooks.toList()
            onCloseHooks.clear()
            copy
        }
        // Invoke hooks outside the lock to avoid holding the lock during the user code.
        for (hook in hooksToRun) {
            hook.invoke()
        }
    }

    public companion object {

        /**
         * The lock for accessing [registry].
         */
        private val registryLock = Any()

        /**
         * Contains [KeyPart] instances produced by the [create] function.
         */
        private val registry = mutableSetOf<KeyPart>()

        public fun create(scope: LoggingScope): KeyPart =
            KeyPart(scope).also { key ->
                synchronized(registryLock) {
                    registry += key
                }
            }

        /**
         * Removes the keys that already do not have referenced scopes.
         */
        public fun removeUnusedKeys() {
            // There are always more specialized keys than entries in the reference queue,
            // so the queue should be empty most of the time we get here.

            // Snapshot the registry to avoid concurrent modification during iteration.
            val snapshot: List<KeyPart> = synchronized(registryLock) {
                registry.toList()
            }

            val dead = ArrayList<KeyPart>()
            for (k in snapshot) {
                if (k.ref.get() == null) {
                    dead += k
                }
            }
            for (k in dead) {
                // Close outside the registry lock.
                k.close()
                synchronized(registryLock) {
                    registry.remove(k)
                }
            }
        }
    }
}
