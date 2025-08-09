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

import io.spine.logging.jvm.Middleman
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.StackSize
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.util.concurrent.TimeUnit.MINUTES

/**
 * Static methods equivalent to the instance methods on
 * [ScopedLoggingContext] but which always operate on the current
 * [ScopedLoggingContext] that would be returned by
 * [ScopedLoggingContext.getInstance].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ScopedLoggingContexts.java"
 *   Original Java code of Google Flogger</a> for historical context.
 */
public object ScopedLoggingContexts {

    private val logger = Middleman.forEnclosingClass()

    @Suppress("MagicNumber")
    private fun warnOnFailure(wasSuccessful: Boolean): Boolean {
        if (!wasSuccessful && !ScopedLoggingContext.getInstance().isNoOp()) {
            val msg = """
                ***** An attempt to add metadata to the current logging context failed. *****
                Calls to static methods in `ScopedLoggingContexts` may fail when there is no
                existing context available. To ensure metadata is available to log statements,
                create a new context via `ScopedLoggingContexts.newContext()` and
                add metadata to it explicitly.
                """.trimIndent()
            logger
                .atWarning()
                .atMostEvery(5, MINUTES)
                .withStackTrace(StackSize.SMALL)
                .log { msg }
        }
        return wasSuccessful
    }

    /**
     * Creates a new [ScopedLoggingContext.Builder] to which additional
     * logging metadata can be attached before being installed or used to wrap
     * some existing code.
     *
     * ```
     * val result = ScopedLoggingContexts.newContext()
     *     .withTags(Tags.of("my_tag", someValue))
     *     .call { MyClass.doFoo() }
     * ```
     */
    @JvmStatic
    public fun newContext(): ScopedLoggingContext.Builder =
        ScopedLoggingContext.getInstance().newContext()

    /**
     * Adds tags by modifying the current context (if one exists).
     *
     * Warning: It is always better to create a new context via
     * [newContext] rather than attempting to modify an existing
     * context. In order of preference you should:
     *
     * 1. Call or wrap a new context with metadata added to it.
     * 2. [ScopedLoggingContext.Builder.install] a new
     *   context and close it when it exits (e.g., if you are using
     *   callbacks to listen to state changes in a task).
     *   However, it is vital the returned [AutoCloseable] is always closed.
     * 3. Call this method and check that it succeeded (e.g., logging a
     *    warning if it fails).
     *
     * The given tags are merged with those of the modified context but
     * existing tags will not be overwritten or removed. This is deliberate
     * since two pieces of code may not know about each other and could
     * accidentally use the same tag name; in that situation it is important
     * that both tag values are preserved.
     *
     * Furthermore, the types of data allowed for tag values are strictly
     * controlled. This is also very deliberate since these tags must be
     * efficiently added to every log statement and so it is important that
     * their resulting string representation is reliably cacheable and can be
     * calculated without invoking arbitrary code (e.g., the `toString()`
     * method of some unknown user type).
     *
     * @param tags the tags to add to the current context.
     * @return `false` if there is no current context, or scoped
     *         contexts are not supported.
     */
    @CanIgnoreReturnValue
    @JvmStatic
    public fun addTags(tags: Tags): Boolean =
        warnOnFailure(ScopedLoggingContext.getInstance().addTags(tags))

    /**
     * Adds a single metadata key/value pair to the current context.
     *
     * Warning: It is always better to create a new context via
     * [newContext] rather than attempting to modify an existing
     * context. In order of preference you should:
     *
     * 1. Call or wrap a new context with metadata added to it.
     * 2. [ScopedLoggingContext.Builder.install] a new
     *   context and close it when you it exits (e.g., if you are using
     *   callbacks to listen to state changes in a task).
     *   However, it is vital that the returned [AutoCloseable] is always closed.
     * 3. Call this method and check it succeeded (e.g., logging a warning if it fails).
     *
     * Unlike [Tags], which have a well-defined value ordering,
     * independent of the order in which values were added, context metadata
     * preserves the order of addition. As such, it is not advised to add
     * values for the same metadata key from multiple threads, since that may
     * create non-deterministic ordering. It is recommended (where possible)
     * to add metadata when building a new context, rather than adding it to
     * context visible to multiple threads.
     *
     * @param T The type of the metadata value.
     * @param key The metadata key.
     * @param value The metadata value.
     * @return `false` if there is no current context, or scoped
     *         contexts are not supported.
     */
    @CanIgnoreReturnValue
    @JvmStatic
    public fun <T : Any> addMetadata(key: MetadataKey<T>, value: T): Boolean =
        warnOnFailure(ScopedLoggingContext.getInstance().addMetadata(key, value))

    /**
     * Applies the given log level map to the current context.
     *
     * Warning: It is always better to create a new context via
     * [newContext] rather than attempting to modify an existing
     * context. In order of preference you should:
     *
     * 1. Call or wrap a new context with metadata added to it.
     * 2. [ScopedLoggingContext.Builder.install] a new
     *   context and close it when you it exits (e.g., if you are using
     *   callbacks to listen to state changes in a task).
     *   However, it is vital that the returned [AutoCloseable] is always closed.
     * 3. Call this method and check that it succeeded (e.g., logging a warning if it fails).
     *
     * Log level settings are merged with any existing setting from the current (or parent)
     * contexts such that logging will be enabled for a log statement if:
     *
     * - It was enabled by the given map.
     * - It was already enabled by the current context.
     *
     * The effects of this call will be undone only when the current context terminates.
     *
     * @param logLevelMap The log level map to apply to the current context.
     * @return `false` if there is no current context, or scoped
     *         contexts are not supported.
     */
    @CanIgnoreReturnValue
    @JvmStatic
    public fun applyLogLevelMap(logLevelMap: LogLevelMap): Boolean {
        return warnOnFailure(ScopedLoggingContext.getInstance().applyLogLevelMap(logLevelMap))
    }
}
