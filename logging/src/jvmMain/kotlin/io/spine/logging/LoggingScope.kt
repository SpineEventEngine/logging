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

import io.spine.annotation.VisibleForTesting

/**
 * JVM implementation of the logging scope which provides garbage collection for the keys
 * via JVM week references.
 *
 * @see <a href="https://github.com/google/flogger/blob/2c7d806b08217993fea229d833a6f8b748591b45/api/src/main/java/com/google/common/flogger/LoggingScope.java">
 *     Original Flogger code</a> for historic reference.
 */
public actual abstract class LoggingScope protected constructor(private val label: String) {

    protected actual abstract fun specialize(key: LogSiteKey): LogSiteKey
    protected actual abstract fun onClose(removalHook: () -> Unit)

    /**
     * Opens [specialize] for the package.
     */
    internal fun doSpecialize(key: LogSiteKey): LogSiteKey = specialize(key)

    /**
     * Opens access to [onClose] for the package.
     */
    internal fun doOnClose(removalHook: () -> Unit) = onClose(removalHook)

    /**
     * Returns the [label] of this scope.
     */
    override fun toString(): String = label

    public companion object {

        /**
         * Creates a scope which automatically removes any associated keys
         * from [io.spine.logging.jvm.LogSiteMap]s when it is garbage collected.
         *
         * The given label is used only for debugging purposes and may appear in log
         * statements, it should not contain any user data or other runtime information.
         */
        @JvmStatic
        public fun create(label: String): LoggingScope = WeakScope(label)
    }

    @VisibleForTesting
    internal class WeakScope(label: String) : LoggingScope(label) {

        /**
         * Do NOT reference the Scope directly from a specialized key, use the "key part"
         * to avoid the key part weak reference is enqueued which triggers tidy up at the next
         * call to `specializeForScopesIn()` where scopes are used.
         *
         * This must be unique per scope since it acts as a qualifier within specialized
         * log site keys. Using a different weak reference per specialized key would not work
         * (which is part of the reason we also need the "on close" queue as well as
         * the reference queue).
         */
        private val keyPart: KeyPart = KeyPart(this)

        override fun specialize(key: LogSiteKey): LogSiteKey =
            SpecializedLogSiteKey.of(key, keyPart)

        override fun onClose(removalHook: () -> Unit) {
            // Clear the reference queue about as often as we would add a new key to a map.
            // This should still mean that the queue is almost always empty when we check
            // it (since we expect more than one specialized log site key per scope) and it
            // avoids spamming the queue clearance loop for every log statement and avoids
            // class loading the reference queue until we know scopes have been used.
            KeyPart.removeUnusedKeys()
            keyPart.addOnCloseHook(removalHook)
        }

        internal fun closeForTesting() {
            keyPart.close()
        }
    }
}
