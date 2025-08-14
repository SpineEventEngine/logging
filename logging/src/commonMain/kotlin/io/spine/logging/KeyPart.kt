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

package io.spine.logging


/**
 * A part of a log site key which has singleton semantics per logging scope.
 *
 * This class is responsible for managing the lifecycle and cleanup of log site keys.
 * It ensures that key references are properly maintained and cleaned up when no longer needed.
 */
public expect class KeyPart {

    /**
     * Adds a hook that will be executed when this key part is closed.
     *
     * @param hook the function to execute on close
     */
    @Suppress("unused") // the parameter is used by `actual` impl.
    public fun addOnCloseHook(hook: () -> Unit)

    /**
     * Closes this key part and executes all registered close hooks.
     * After closing, the key part becomes invalid and should not be used.
     */
    public fun close()

    public companion object {
        /**
         * Removes keys that are no longer in use from the internal storage.
         * This helps prevent memory leaks by cleaning up abandoned key references.
         */
        public fun removeUnusedKeys()
    }
}
