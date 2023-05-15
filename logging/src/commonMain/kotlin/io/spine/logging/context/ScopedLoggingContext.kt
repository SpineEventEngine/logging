/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * This is a user-centric API for constructing and modifying logging contexts
 * within an application.
 *
 * Logging contexts are scoped, allowing application code to attach metadata and
 * control logging within specific bounds. It's commonly used for modifying logging
 * behaviour on a "per request" basis, for example:
 * - Attaching a request ID to each log statement.
 * - Enabling detailed logging for a specific request (like based on a URL debug parameter).
 *
 * You can nest contexts, and add new ones to supply more metadata, accessible to
 * logging as long as the context is active.
 *
 * Logging contexts are designed with the intention of preventing accidental "undoing"
 * of existing behaviour due to modifications by independent libraries or helper functions.
 *
 * For example, a nested context can't disable logging enabled by its parent context.
 * As a consequence, you can't disable logging from within a context.
 * This is by design as overly verbose logging should be handled via other
 * means (like code amendments, global logging configuration), not on
 * a "per request" basis.
 *
 * Depending on a framework, the current logging context might be automatically
 * propagated to threads or sub-tasks initiated within the context. However,
 * this isn't a guarantee and the behaviour isn't defined by this class.
 *
 * Note, there's no assurance of a default "global" context if you haven't explicitly
 * opened a context.
 */
public interface ScopedLoggingContext {

    /**
     * A fluent API for [creating][newContext] and [installing][install] new
     * logging context scopes.
     */
    public interface Builder {

        /**
         * Sets the log level map to be used with the context being built.
         *
         * This method can be called at most once per builder. Calling more than
         * once will result in a runtime error.
         */
        public fun withLogLevelMap(map: LogLevelMap): Builder


        public fun install(): AutoCloseable
    }

    public companion object {

        /**
         * Creates a builder for the new logging context.
         */
        @JvmStatic
        public fun newContext(): Builder = LoggingContextFactory.newContext()
    }
}
