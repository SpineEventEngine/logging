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

package io.spine.logging.jvm.context;

import io.spine.logging.jvm.Middleman;
import io.spine.logging.jvm.MetadataKey;
import io.spine.logging.jvm.StackSize;
import io.spine.logging.jvm.context.ScopedLoggingContext.LoggingContextCloseable;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

/**
 * Fallback context data provider used when no other implementations are available for a platform.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/NoOpContextDataProvider.java">
 *         Original Java code of Google Flogger</a>
 */
final class NoOpContextDataProvider extends ContextDataProvider {

    private static final ContextDataProvider NO_OP_INSTANCE = new NoOpContextDataProvider();

    /**
     * Returns a singleton "no op" instance of the context data provider API which logs a warning if
     * used in code which attempts to set context information or modify scopes.
     * This is intended for use by platform implementations in cases where no context is configured.
     */
    static ContextDataProvider getNoOpInstance() {
        return NO_OP_INSTANCE;
    }

    private static final class NoOpScopedLoggingContext extends ScopedLoggingContext
            implements LoggingContextCloseable {

        // Since the ContextDataProvider class is loaded during Platform initialization we must be very
        // careful to avoid any attempt to obtain a logger instance until we can be sure logging config
        // is complete.
        private static final class LazyLogger {

            private static final Middleman logger = Middleman.forEnclosingClass();
        }

        private final AtomicBoolean haveWarned = new AtomicBoolean();

        private void logWarningOnceOnly() {
            if (haveWarned.compareAndSet(false, true)) {
                var defaultPlatform = "io.spine.logging.backend.system.DefaultPlatform";
                LazyLogger.logger
                        .atWarning()
                        .withStackTrace(StackSize.SMALL)
                        .log(format(
                                "Scoped logging contexts are disabled; no context data provider was installed.%n"
                                        +
                                        "To enable scoped logging contexts in your application, see the "
                                        +
                                        "site-specific Platform class used to configure logging behaviour.%n"
                                        + "Default Platform: `%s`.",
                                defaultPlatform)
                        );
            }
        }

        @Override
        public ScopedLoggingContext.Builder newContext() {
            return new ScopedLoggingContext.Builder() {
                @Override
                public LoggingContextCloseable install() {
                    logWarningOnceOnly();
                    return NoOpScopedLoggingContext.this;
                }
            };
        }

        @Override
        public ScopedLoggingContext.Builder newContext(ScopeType scopeType) {
            // Ignore scope bindings when there's no way to propagate them.
            return newContext();
        }

        @Override
        public boolean addTags(Tags tags) {
            logWarningOnceOnly();
            // Superclass methods still do argument checking, which is important for consistent behaviour.
            return super.addTags(tags);
        }

        @Override
        public <T> boolean addMetadata(MetadataKey<T> key, T value) {
            logWarningOnceOnly();
            return super.addMetadata(key, value);
        }

        @Override
        public boolean applyLogLevelMap(LogLevelMap logLevelMap) {
            logWarningOnceOnly();
            return super.applyLogLevelMap(logLevelMap);
        }

        @Override
        public void close() {
        }

        @Override
        boolean isNoOp() {
            return true;
        }
    }

    private final ScopedLoggingContext noOpContext = new NoOpScopedLoggingContext();

    @Override
    public ScopedLoggingContext getContextApiSingleton() {
        return noOpContext;
    }

    @Override
    public String toString() {
        return "No-op Provider";
    }
}
