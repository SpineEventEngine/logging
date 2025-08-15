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

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.errorprone.annotations.MustBeClosed
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.context.ScopedLoggingContext.Companion.getInstance
import java.util.concurrent.Callable

/**
 * A user-facing API for creating and modifying scoped logging contexts in applications.
 *
 * Scoped contexts provide a way for application code to attach metadata and
 * control the behaviour of logging within well-defined contexts.
 * This is most often associated with making "per request" modifications to
 * logging behaviour such as:
 *
 * - Adding a request ID to every log statement.
 * - Forcing logging at a finer level for a specific request
 *   (e.g., based on a URL debug parameter).
 *
 * Contexts are nestable and new contexts can be added to provide additional metadata which will
 * be available to logging as long as the context is installed.
 *
 * Note that in the current API contexts are also modifiable after creation, but this usage is
 * discouraged and may be removed in the future. The problem with modifying contexts after creation
 * is that, since contexts can be shared between threads, it is potentially confusing if tags are
 * added to a context when it is being used concurrently by multiple threads.
 *
 * Note that since logging contexts are designed to be modified by code in libraries and helper
 * functions which do not know about each other, the data structures and behaviour of logging
 * contexts are carefully designed to avoid any accidental "undoing" of existing behaviour.
 * In particular:
 *
 * - Tags can only be added to contexts, never modified or removed.
 * - Logging that's enabled by one context cannot be disabled from within a nested context.
 *
 * One possibly surprising result of this behaviour is that it's not possible to disable logging
 * from within a context. However, this is quite intentional, since overly verbose logging should be
 * fixed by other mechanisms (code changes, global logging configuration), and not on a "per
 * request" basis.
 *
 * Depending on the framework used, it's possible that the current logging context will be
 * automatically propagated to some or all threads or subtasks started from within the context.
 * This is not guaranteed, however, and the semantic behaviour of context propagation is not defined
 * by this class.
 *
 * In particular, if you haven't explicitly opened a context in which to run your code, there is
 * no guarantee that a default "global" context exists. In this case any attempts to add metadata
 * (e.g. via [addTags]) will fail, returning `false`.
 *
 * Context support and automatic propagation is heavily reliant on Java platform capabilities,
 * and precise behaviour is likely to differ between runtime environments or frameworks. Context
 * propagation may not behave the same everywhere, and in some situations logging contexts may not
 * be supported at all. Methods which attempt to affect a context state may do nothing in some
 * environments or when called at some points in an application. If application code relies on
 * modifications to an existing, implicit logging context, it should always check the return values
 * of any modification methods called (e.g. [addTags]).
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ScopedLoggingContext.java)
 * for historical context.
 */
public abstract class ScopedLoggingContext protected constructor() {

    /**
     * A fluent builder API for creating and installing new context scopes.
     *
     * This API should be used whenever the metadata to be added to a scope is known at
     * the time the scope is created.
     *
     * This class is intended to be used only as part of a fluent statement, and retaining a
     * reference to a builder instance for any length of time is not recommended.
     */
    @Suppress("TooManyFunctions")
    public abstract class Builder protected constructor() {

        private var tags: Tags? = null
        private var metadata: ContextMetadata.Builder? = null
        private var logLevelMap: LogLevelMap? = null

        /**
         * Sets the tags to be used with the context.
         *
         * This method can be called at most once per builder.
         */
        @CanIgnoreReturnValue
        public fun withTags(tags: Tags): Builder {
            check(this.tags == null)  { "Tags already set: `$tags`." }
            this.tags = tags
            return this
        }

        /**
         * Adds a single metadata key/value pair to the context. This method can be called multiple
         * times on a builder.
         */
        @CanIgnoreReturnValue
        public fun <T : Any> withMetadata(key: MetadataKey<T>, value: T): Builder {
            if (metadata == null) {
                metadata = ContextMetadata.builder()
            }
            metadata!!.add(key, value)
            return this
        }

        /**
         * Sets the log level map to be used with the context being built.
         *
         * This method can be called at most once per builder.
         */
        @CanIgnoreReturnValue
        public fun withLogLevelMap(logLevelMap: LogLevelMap): Builder {
            check(this.logLevelMap == null) {
                "Log level map already set: `$logLevelMap`."
            }
            this.logLevelMap = logLevelMap
            return this
        }

        /**
         * Wraps a runnable so it will execute within a new context based on
         * the state of the builder.
         *
         * Note that each time this runnable is executed, a new context will be
         * installed extending from the currently installed context at the time of execution.
         *
         * @throws InvalidLoggingContextStateException
         *         if the context created during this method cannot
         *         be closed correctly (e.g., if a nested context has also been opened,
         *         but not closed).
         */
        public fun wrap(r: Runnable): Runnable {
            return Runnable {
                val context = install()
                var hasError = true
                try {
                    r.run()
                    hasError = false
                } finally {
                    closeAndMaybePropagateError(context, hasError)
                }
            }
        }

        /**
         * Wraps a callable so it will execute within a new context based on
         * the state of the builder.
         *
         * Note that each time this runnable is executed, a new context will be installed extending
         * from the currently installed context at the time of execution.
         *
         * @throws InvalidLoggingContextStateException
         *         if the context created during this method cannot
         *         be closed correctly (e.g., if a nested context has also been opened, but not
         *         closed).
         */
        public fun <R> wrap(c: Callable<R>): Callable<R> {
            return Callable {
                val context = install()
                var hasError = true
                try {
                    val result = c.call()
                    hasError = false
                    result
                } finally {
                    closeAndMaybePropagateError(context, hasError)
                }
            }
        }

        /**
         * Runs a runnable directly within a new context installed from this builder.
         */
        public fun run(r: Runnable): Unit =
            wrap(r).run()

        /**
         * Calls a [Callable] directly within a new context installed from this builder.
         */
        @CanIgnoreReturnValue
        @Throws(Exception::class)
        public fun <R> call(c: Callable<R>): R =
            wrap(c).call()

        /**
         * Calls a [Callable] directly within a new context installed from this builder,
         * wrapping any checked exceptions with a [RuntimeException].
         */
        @CanIgnoreReturnValue
        @Suppress("TooGenericExceptionCaught")
        public fun <R> callUnchecked(c: Callable<R>): R = try {
            call(c)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Checked exception caught during context call. ", e)
        }

        /**
         * Installs a new context based on the state of the builder.
         *
         * The caller is *required* to invoke [AutoCloseable.close][AutoCloseable] on
         * the returned instances in the reverse order to which they were obtained:
         *
         * ```kotlin
         * ScopedLoggingContext.getInstance().newContext()
         *     .withTags(Tags.of("my_tag", someValue).install().use {
         *     // Logging by code called from within this context will
         *     // contain the additional metadata.
         *     logger.atInfo().log("Log message should contain tag value...");
         * }
         * ```
         *
         * To avoid the need to manage contexts manually, it is strongly recommended that the
         * helper methods, such as [wrap] or [run] are used to simplify
         * the handling of contexts. This method is intended primarily to be overridden by context
         * implementations rather than being invoked as a normal part of context use.
         *
         * An implementation of scoped contexts must preserve any existing metadata when a
         * context is opened, and restore the previous state when it terminates.
         *
         * Note that the returned [AutoCloseable] is not required to enforce the
         * correct closure of nested contexts, and while it is permitted to throw a
         * [InvalidLoggingContextStateException] in the face of mismatched or invalid usage,
         * it is not required.
         */
        @MustBeClosed
        public abstract fun install(): AutoCloseable

        /**
         * Returns the configured tags, or null.
         *
         * This method may do work and results should be cached by context implementations.
         */
        protected fun getTags(): Tags? = tags

        /**
         * Returns the configured context metadata, or null.
         *
         * This method may do work and results should be cached by context implementations.
         */
        protected fun getMetadata(): ContextMetadata? = metadata?.build()

        /**
         * Returns the configured log level map, or null.
         *
         * This method may do work and results should be cached by context implementations.
         */
        protected fun getLogLevelMap(): LogLevelMap? = logLevelMap
    }

    /**
     * Creates a new context builder to which additional logging metadata can be attached before
     * being installed or used to wrap some existing code.
     *
     * ```kotlin
     * val ctx = ScopedLoggingContext.getInstance()
     * val result = ctx.newContext().withTags(Tags.of("my_tag", someValue)).call { MyClass.doFoo() }
     * ```
     *
     * Implementations of this API must return a subclass of [Builder] which can install
     * all necessary metadata into a new context from the builder's current state.
     *
     * Note for users: if you do not need an instance of `ScopedLoggingContext` for some
     * reason such as testability (injecting it, for example), consider using the static methods in
     * [ScopedLoggingContexts] instead to avoid the need to call [getInstance]:
     *
     * ```kotlin
     * val result = ScopedLoggingContexts.newContext()
     *     .withTags(Tags.of("my_tag", someValue))
     *     .call { MyClass.doFoo() }
     * ```
     */
    public abstract fun newContext(): Builder

    /**
     * Creates a new context builder to which additional logging metadata can be attached before
     * being installed or used to wrap some existing code.
     *
     * This method is the same as [newContext] except it additionally binds a new
     * [ScopeType] instance to the newly created context.
     * This allows log statements to control stateful logging operations (e.g., rate limiting)
     * using [io.spine.logging.LoggingApi.per] method.
     *
     * Note for users: if you do not need an instance of `ScopedLoggingContext` for some
     * reason such as testability (injecting it, for example), consider using the static methods in
     * [ScopedLoggingContexts] instead to avoid the need to call [getInstance].
     */
    public abstract fun newContext(scopeType: ScopeType?): Builder

    /**
     * Deprecated equivalent to [newContext].
     *
     * @deprecated implementers and callers should use [newContext] instead. This method will
     *         be removed in the next Flogger release.
     */
    @Deprecated("Use newContext() instead", ReplaceWith("newContext()"))
    public fun newScope(): Builder {
        return newContext()
    }

    /**
     * Adds tags to the current set of log tags for the current context.
     *
     * Tags are merged together and existing tags cannot be modified.
     * This is deliberate since two pieces of code may not know about each other and
     * could accidentally use the same tag name; in that situation it is important
     * that both tag values are preserved.
     *
     * Furthermore, the types of data allowed for tag values are strictly controlled.
     * This is also very deliberate since these tags must be efficiently added to every
     * log statement and so it is important that their resulting string representation is
     * reliably cacheable and can be calculated without invoking arbitrary code
     * (e.g., the `toString()` method of some unknown user type).
     *
     * @return false if there is no current context, or scoped contexts are not supported.
     */
    @CanIgnoreReturnValue
    public open fun addTags(tags: Tags): Boolean {
        //TODO:2025-06-30:alexander.yevsyukov: Investigate why this method does nothing despite
        // the documentation.
        return false
    }

    /**
     * Adds a single metadata key/value pair to the current context.
     *
     * Unlike [Tags], which have a well-defined value ordering, independent of the order
     * in which values were added, context metadata preserves the order of addition.
     * As such, it is not advised to add values for the same metadata key from multiple threads,
     * since that may create non-deterministic ordering.
     *
     * It is recommended (where possible) to add metadata when building
     * a new context, rather than adding it to context visible to multiple threads.
     */
    @CanIgnoreReturnValue
    public open fun <T : Any> addMetadata(key: MetadataKey<T>, value: T): Boolean {
        //TODO:2025-06-30:alexander.yevsyukov: Investigate why this method does nothing despite
        // the documentation.
        return false
    }

    /**
     * Applies the given log level map to the current context.
     *
     * Log level settings are merged with any existing setting from the current (or parent)
     * contexts such that logging will be enabled for a log statement if:
     *
     * - It was enabled by the given map.
     * - It was already enabled by the current context.
     *
     * The effects of this call will be undone only when the current context terminates.
     *
     * @return `false` if there is no current context, or scoped contexts are not supported.
     */
    @CanIgnoreReturnValue
    public open fun applyLogLevelMap(logLevelMap: LogLevelMap): Boolean {
        //TODO:2025-06-30:alexander.yevsyukov: Investigate why this method does nothing despite
        // the documentation.
        return false
    }

    /**
     * Package-private checker to help avoid unhelpful debug logs.
     */
    public open fun isNoOp(): Boolean = false

    public companion object {
        /**
         * Returns the platform/framework-specific implementation of the logging context API.
         *
         * This is a singleton value and need not be cached by callers.
         * If logging contexts are not supported, this method will return
         * an empty context implementation which has no effect.
         */
        @JvmStatic
        public fun getInstance(): ScopedLoggingContext =
            ContextDataProvider.getInstance().getContextApiSingleton()

        @Suppress("TooGenericExceptionCaught")
        private fun closeAndMaybePropagateError(
            context: AutoCloseable,
            callerHasError: Boolean
        ) {
            // Because AutoCloseable is not just a `Closeable`, there's no risk of it
            // throwing any checked exceptions.
            // In particular, when this is switched to use `AutoCloseable`, there's no risk of
            // having to deal with InterruptedException.
            // That's why having an extended interface is always better than
            // using [Auto]Closeable directly.
            try {
                context.close()
            } catch (e: RuntimeException) {
                // This method is always called from a `finally` block which may
                // be about to rethrow a user exception, so ignore any errors
                // during `close()` if that's the case.
                if (!callerHasError) {
                    throw e as? InvalidLoggingContextStateException
                        ?: InvalidLoggingContextStateException("Invalid logging context state.", e)
                }
            }
        }
    }
}
