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

package io.spine.logging.context

import io.spine.logging.LoggingScope

/**
 * Lightweight internal helper class for context implementations to manage a list of scopes.
 */
public class ScopeItem(
    private val key: ScopeType,
    private val scope: LoggingScope,
    private val next: ScopeItem?
) {
    public companion object {

        /**
         * Adds a new scope to the list for the given type.
         *
         * If the given type is null, or a scope for the type already exists in the list,
         * the original (potentially `null`) list reference is returned.
         */
        @JvmStatic
        public fun addScope(list: ScopeItem?, type: ScopeType?): ScopeItem? {
            return if (type != null && lookup(list, type) == null) {
                ScopeItem(type, type.newScope(), list)
            } else {
                list
            }
        }

        /**
         * Finds a scope instance for the given type in a possibly null scope list.
         */
        @JvmStatic
        public fun lookup(list: ScopeItem?, type: ScopeType): LoggingScope? {
            var currentList = list
            while (currentList != null) {
                if (type == currentList.key) {
                    return currentList.scope
                }
                currentList = currentList.next
            }
            return null
        }
    }
}
