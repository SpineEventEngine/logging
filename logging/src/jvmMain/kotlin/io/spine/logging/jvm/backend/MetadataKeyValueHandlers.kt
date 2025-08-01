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

package io.spine.logging.jvm.backend

import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.MetadataKey.KeyValueHandler

/**
 * A helper class providing the default callbacks and handlers for processing
 * metadata as key/value pairs.
 *
 * It is expected that most text-based logger backends will format unknown metadata
 * using the handlers from this class.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MetadataKeyValueHandlers.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public object MetadataKeyValueHandlers {

    private val EMIT_METADATA = ValueHandler<Any, KeyValueHandler> { key, value, handler ->
        key.safeEmit(value, handler)
    }

    private val EMIT_REPEATED_METADATA =
        RepeatedValueHandler<Any, KeyValueHandler> { key, values, handler ->
            key.safeEmitRepeated(values, handler)
        }

    /**
     * Returns a singleton value handler which dispatches metadata to a [KeyValueHandler].
     */
    @JvmStatic
    public fun getDefaultValueHandler(): ValueHandler<Any, KeyValueHandler> =
        EMIT_METADATA

    /**
     * Returns a singleton value handler which dispatches metadata to a [KeyValueHandler].
     */
    @JvmStatic
    public fun getDefaultRepeatedValueHandler(): RepeatedValueHandler<Any, KeyValueHandler> =
        EMIT_REPEATED_METADATA

    /**
     * Returns a new [MetadataHandler.Builder] which handles all non-ignored
     * metadata keys by dispatching their values to the key itself.
     *
     * This is convenient for generic metadata processing when used in conjunction with
     * something like [KeyValueFormatter].
     *
     * The returned builder can be built immediately or customized further to handler some keys
     * specially (e.g., allowing keys/values to modify logging behaviour).
     *
     * @return a builder configured with the default key/value handlers and ignored keys.
     */
    @JvmStatic
    public fun getDefaultBuilder(
        ignored: Set<MetadataKey<*>>
    ): MetadataHandler.Builder<KeyValueHandler> =
        MetadataHandler.builder(getDefaultValueHandler())
            .setDefaultRepeatedHandler(getDefaultRepeatedValueHandler())
            .ignoring(ignored)

    /**
     * Returns a new [MetadataHandler] which handles all non-ignored
     * metadata keys by dispatching their values to the key itself.
     *
     * This is convenient for generic metadata processing when used in
     * conjunction with something like [KeyValueFormatter].
     *
     * @return a handler configured with the default key/value handlers and ignored keys.
     */
    @JvmStatic
    public fun getDefaultHandler(
        ignored: Set<MetadataKey<*>>
    ): MetadataHandler<KeyValueHandler> = getDefaultBuilder(ignored).build()
}
