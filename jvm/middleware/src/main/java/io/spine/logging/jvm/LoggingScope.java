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

package io.spine.logging.jvm;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.spine.logging.jvm.backend.Metadata;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An opaque scope marker which can be attached to log sites to provide "per scope" behaviour for
 * stateful logging operations (e.g. rate limiting).
 *
 * <p>Scopes are provided via the {@link LoggingScopeProvider} interface and found by looking for
 * the current {@link io.spine.logging.jvm.context.ScopedLoggingContext ScopedLoggingContexts}.
 *
 * <p>Stateful fluent logging APIs which need to look up per log site information (e.g. rate limit
 * state) should do so via a {@link LogSiteMap} using the {@link LogSiteKey} passed into the
 * {@link LogContext#postProcess(LogSiteKey)} method. If scopes are present in the log site
 * {@link Metadata} then the log site key provided to the {@code postProcess()} method will already
 * be specialized to take account of any scopes present.
 *
 * <p>Note that scopes have no effect when applied to stateless log statements (e.g. log statements
 * without rate limiting) since the log site key for that log statement will not be used in any
 * maps.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LoggingScope.java">
 *         Original Java code of Google Flogger</a> for historical context.
 */
public abstract class LoggingScope {

    /**
     * Creates a scope which automatically removes any associated keys from {@link LogSiteMap}s
     * when
     * it's garbage collected. The given label is used only for debugging purposes and may appear in
     * log
     * statements, it should not contain any user data or other runtime information.
     */
    // TODO: Strongly consider making the label a compile time constant.
    public static LoggingScope create(String label) {
        return new WeakScope(checkNotNull(label, "label"));
    }

    private final String label;

    /**
     * Creates a basic scope with the specified label. Custom subclasses of {@code LoggingScope}
     * must
     * manage their own lifecycles to avoid leaking memory and polluting {@link LogSiteMap}s with
     * unused
     * keys.
     */
    protected LoggingScope(String label) {
        this.label = label;
    }

    /**
     * Returns a specialization of the given key which accounts for this scope instance.
     *
     * <p>Two specialized keys should compare as {@link Object#equals(Object)} if and only if
     * they are specializations from the same log site, with the same sequence of scopes applied.
     *
     * <p>The returned instance:
     *
     * <ul>
     *   <li>Must be an immutable "value type".
     *   <li>Must not compare as {@link Object#equals(Object)} to the given key.
     *   <li>Should have a different {@link Object#hashCode()} to the given key.
     *   <li>Should be efficient and lightweight.
     * </ul>
     *
     * As such it is recommended that the {@link SpecializedLogSiteKey#of(LogSiteKey, Object)}
     * method is used in implementations, passing in a suitable qualifier (which need not be
     * the scope itself, but must be unique per scope).
     */
    protected abstract LogSiteKey specialize(LogSiteKey key);

    /**
     * Registers "hooks" which should be called when this scope is "closed".
     *
     * <p>The hooks are intended to remove the keys associated with this scope from any data
     * structures they may be held in, to avoid leaking allocations.
     *
     * <p>Note that a key may be specialized with several scopes and the first scope to be closed
     * will remove it from any associated data structures (conceptually the scope that a log site is
     * called from is the intersection of all the currently active scopes which apply to it).
     */
    protected abstract void onClose(Runnable removalHook);

    @Override
    public final String toString() {
        return label;
    }

    @VisibleForTesting
    static final class WeakScope extends LoggingScope {

        /**
         * Do NOT reference the Scope directly from a specialized key, use the "key part" to avoid
         * the key part weak reference is enqueued which triggers tidyup at the next call to
         * {@code specializeForScopesIn()} where scopes are used.
         *
         * <p>This must be unique per scope since it acts as a qualifier within specialized log site
         * keys. Using a different weak reference per specialized key would not work (which is part
         * of the reason we also need the "on close" queue as well as the reference queue).
         */
        private final KeyPart keyPart;

        public WeakScope(String label) {
            super(label);
            this.keyPart = new KeyPart(this);
        }

        @Override
        protected LogSiteKey specialize(LogSiteKey key) {
            return SpecializedLogSiteKey.of(key, keyPart);
        }

        @Override
        protected void onClose(Runnable remove) {
            // Clear the reference queue about as often as we would add a new key to a map.
            // This  should still mean that the queue is almost always empty when we check
            // it (since we expect more than one specialized log site key per scope) and it
            // avoids spamming the queue clearance loop for every log statement and avoids
            // class loading the reference queue until we know scopes have been used.
            KeyPart.removeUnusedKeys();
            keyPart.onCloseHooks.offer(remove);
        }

        void closeForTesting() {
            keyPart.close();
        }

        /**
         * ThisClass is only loaded once we've seen scopes in action
         * (Android doesn't like eager class loading, and many Android apps won't use scopes).
         * This forms part of each log site key, some must have singleton semantics.
         */
        private static class KeyPart extends WeakReference<LoggingScope> {

            private static final ReferenceQueue<LoggingScope> queue = new ReferenceQueue<>();

            private final Queue<Runnable> onCloseHooks = new ConcurrentLinkedQueue<>();

            private KeyPart(LoggingScope scope) {
                super(scope, queue);
            }

            // If this were ever too "bursty" due to removal of many keys for the same scope,
            // we could modify this code to process only a maximum number of removals each
            // time and keep a single "in progress" KeyPart around until next time.
            private static void removeUnusedKeys() {
                // There are always more specialized keys than entries in the reference queue,
                // so the queue should be empty most of the time we get here.
                for (KeyPart p = (KeyPart) queue.poll(); p != null; p = (KeyPart) queue.poll()) {
                    p.close();
                }
            }

            private void close() {
                // This executes once for each map entry created in the enclosing scope.
                // It is very dependent on logging usage in the scope and theoretically unbounded.
                for (var r = onCloseHooks.poll(); r != null; r = onCloseHooks.poll()) {
                    r.run();
                }
            }
        }
    }
}
