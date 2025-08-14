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

import io.spine.logging.jvm.LoggingScope
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * This class is only loaded once we've seen scopes in action (Android doesn't like
 * eager class loading, and many Android apps won't use scopes). This forms part of each
 * log site key, so must have singleton semantics per scope.
 *
 * This file encapsulates Java-specific weak-reference and concurrent queue usage to keep
 * the [LoggingScope] API Kotlin-only.
 */
public actual class KeyPart internal constructor(scope: LoggingScope) :
    WeakReference<LoggingScope>(scope, queue) {

    private val onCloseHooks = ConcurrentLinkedQueue<() -> Unit>()

    public actual fun addOnCloseHook(hook: () -> Unit) {
        onCloseHooks.offer(hook)
    }

    // If this were ever too "bursty" due to removal of many keys for the same scope,
    // we could modify this code to process only a maximum number of removals each time
    // and keep a single "in progress" KeyPart around until the next time.
    public actual fun close() {
        // This executes once for each map entry created in the enclosing scope.
        // It is very dependent on logging usage in the scope and theoretically unbounded.
        var r = onCloseHooks.poll()
        while (r != null) {
            r()
            r = onCloseHooks.poll()
        }
    }

    public actual companion object {
        private val queue = ReferenceQueue<LoggingScope>()

        public actual fun removeUnusedKeys() {
            // There are always more specialized keys than entries in the reference queue,
            // so the queue should be empty most of the time we get here.
            var p = queue.poll() as KeyPart?
            while (p != null) {
                p.close()
                p = queue.poll() as KeyPart?
            }
        }
    }
}
