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

package io.spine.logging.backend

import io.spine.logging.MetadataKey

/**
 * A sequence of metadata key/value pairs which can be associated to a log statement,
 * either directly via methods in the fluent API, of as part of a scoped logging context.
 *
 * Metadata keys can be "single valued" or "repeating" based on [MetadataKey.canRepeat],
 * but it is permitted for a `Metadata` implementation to retain multiple single valued
 * keys, and in that situation the key at the largest index is the one that should be used.
 *
 * Multiple `Metadata` instances can be merged, in order, to provide a final sequence for
 * a log statement. When `Metadata` instance are merged, the result is just the concatenation
 * of the sequence of key/value pairs, and this is what results in the potential for multiple
 * single valued keys to exist.
 *
 * If the value of a single valued key is required, the [findValue] method should
 * be used to look it up. For all other metadata processing, a [MetadataProcessor]
 * should be created to ensure that scope and log site metadata can be merged correctly.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/Metadata.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class Metadata {

    public companion object {

        /**
         * Returns an immutable `Metadata` that has no items.
         */
        @JvmStatic
        public fun empty(): Metadata = EmptyMetadata
    }

    /**
     * Returns the number of key/value pairs for this instance.
     */
    public abstract fun size(): Int

    /**
     * Returns the key for the Nth piece of metadata.
     *
     * @throws IndexOutOfBoundsException if either `n` is negative or `n` is greater
     *   or equal to `getCount()`.
     */
    public abstract fun getKey(n: Int): MetadataKey<Any>

    /**
     * Returns the non-null value for the Nth piece of metadata.
     *
     * @throws IndexOutOfBoundsException if either `n` is negative or `n` is greater
     *  or equal to `getCount()`.
     */
    public abstract fun getValue(n: Int): Any

    /**
     * Returns the first value for the given single valued metadata key, or null if it does not exist.
     *
     * @throws NullPointerException if `key` is `null`.
     */
    // TODO(dbeaumont): Make this throw an exception for repeated keys.
    public abstract fun <T : Any> findValue(key: MetadataKey<T>): T?
}

/**
 * Implementation of empty metadata as a Kotlin object to optimize class loading.
 * This decoupled approach helps avoid unnecessary class loading, which is especially
 * important for Android applications.
 */
private object EmptyMetadata : Metadata() {
    override fun size(): Int = 0
    override fun getKey(n: Int): MetadataKey<Any> = throw cannotReadFromEmpty()
    override fun getValue(n: Int): Any = throw cannotReadFromEmpty()
    private fun cannotReadFromEmpty(): IndexOutOfBoundsException =
        throw IndexOutOfBoundsException("cannot read from empty metadata")
    override fun <T : Any> findValue(key: MetadataKey<T>): T? = null
}
