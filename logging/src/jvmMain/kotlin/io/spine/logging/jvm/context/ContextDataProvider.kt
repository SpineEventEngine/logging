/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.context

import io.spine.logging.LoggingScope
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.backend.Platform
import java.util.logging.Level

/**
 * An API for injecting scoped metadata for log statements (either globally or on
 * a per-request basis).
 *
 * This class is not a public API and should never need to be invoked directly by
 * application code.
 *
 * Note that since this class (and any installed implementation subclass) is loaded when the
 * logging platform is loaded, care must be taken to avoid cyclic references during static
 * initialization. This means that no static fields or static initialization can reference fluent
 * loggers or the logging platform (either directly or indirectly).
 *
 * ## This is a service type
 *
 * This type is considered a *service type* and implementations may be loaded from the
 * classpath via [java.util.ServiceLoader] provided the proper service metadata is included in
 * the jar file containing the implementation. When creating an implementation of this class, you
 * can provide service metadata (and thereby allow users to get your implementation just by
 * including your jar file) by either manually including
 * a `META-INF/services/io.spine.logging.jvm.context.ContextDataProvider` file
 * containing the name of your implementation class or by annotating your implementation class
 * using [`@AutoService(ContextDataProvider.class)`](https://github.com/google/auto/tree/master/service).
 * See the documentation of both [java.util.ServiceLoader] and `DefaultPlatform`
 * for more information.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ContextDataProvider.java)
 * for historical context.
 */
public abstract class ContextDataProvider {

    /**
     * Returns the context API with which users can create and modify
     * the state of logging contexts within an application.
     *
     * This method should be overridden by subclasses to provide
     * the specific implementation of the API.
     *
     * This method should never be called directly (other than in tests), and
     * users should always go via [ScopedLoggingContext.getInstance],
     * without needing to reference this class at all.
     *
     * If an implementation wishes to allow logging from the context API class,
     * that class must be lazily loaded when this method is called (e.g., using a "lazy holder").
     * Failure to do so is likely to result in errors during the initialization of
     * the logging platform classes.
     */
    public abstract fun getContextApiSingleton(): ScopedLoggingContext

    /**
     * Returns whether the given logger should have logging forced at the specified level.
     *
     * When logging is forced for a log statement, it will be emitted regardless or
     * the normal log level configuration of the logger and ignoring any rate limiting or
     * other filtering.
     *
     * Implementations which do not support forced logging should not override this method;
     * the default implementation returns `false`.
     *
     * `loggerName` can be used to look up specific configuration, such as log level,
     * for the logger, to decide if a log statement should be forced.
     * This information might vary depending on the context in which this call is made,
     * so the result should not be cached.
     *
     * `isEnabledByLevel` indicates that the log statement is enabled according to its log
     * level, but a `true` value does not necessarily indicate that logging will occur, due to
     * rate limiting or other conditional logging mechanisms.
     *
     * To bypass conditional logging and ensure that an enabled log statement will be emitted,
     * this method should return `true` if `isEnabledByLevel` was `true`.
     *
     * WARNING: This method MUST complete quickly and without allocating any memory.
     * It is invoked for every log statement regardless of logging configuration,
     * so any implementation must go to every possible length to be efficient.
     *
     * @param loggerName The fully qualified logger name (e.g., `"com.example.SomeClass"`)
     * @param level The level of the log statement being invoked.
     * @param isEnabledByLevel Whether the logger is enabled at the given level.
     */
    public open fun shouldForceLogging(
        loggerName: String,
        level: Level,
        isEnabledByLevel: Boolean
    ): Boolean = false

    /**
     * Obtains a custom logging level set for the logger with the given name.
     *
     * The default implementation always returns `null`.
     *
     * @param loggerName The name of the logger.
     * @return the custom level set for the logger or `null` if the level is not set.
     */
    public open fun getMappedLevel(loggerName: String): Level? = null

    /**
     * Returns a set of tags to be added to a log statement.
     *
     * These tags can be used to provide additional contextual metadata
     * to log statements (e.g., request IDs).
     *
     * Implementations which do not support scoped [Tags] should not override this method;
     * the default implementation returns [Tags.empty].
     */
    public open fun getTags(): Tags = Tags.empty()

    /**
     * Returns metadata to be applied to a log statement.
     *
     * Scoped metadata can be used to provide structured data to log statements or
     * control logging behaviour (in conjunction with a custom logger backend).
     *
     * Implementations which do not support scoped [Metadata] should not override this
     * method; the default implementation returns [Metadata.empty].
     */
    public open fun getMetadata(): Metadata = Metadata.empty()

    /**
     * Returns the scope instance of the specified type for this context, or `null`
     * if no such scope was bound to this context.
     *
     * This method searches parent contexts as well.
     *
     * Implementations which do not support scope types should return `null`,
     * which can be achieved by using the default method.
     */
    public open fun getScope(type: ScopeType): LoggingScope? = null

    public companion object {

        /**
         * Returns the singleton instance of the context data provider for use by
         * logging platform implementations.
         *
         * This method should not be called by general application code, and
         * the `ContextDataProvider` class should never need to be used directly
         * outside of the logger platform implementations.
         */
        @JvmStatic
        public fun getInstance(): ContextDataProvider =
            Platform.getContextDataProvider()

        /**
         * Returns the singleton no-op context data provider, which can be used by platform
         * implementations which do not support `ScopedLoggingContext` for some reason.
         *
         * The returned provider has no effect and returns empty/default data in all cases.
         *
         * In general, this method should never need to be called outside
         * the core of the Logging library.
         */
        @JvmStatic
        public fun getNoOpProvider(): ContextDataProvider =
            NoOpContextDataProvider.noOpInstance
    }
}
