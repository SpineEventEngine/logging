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

package io.spine.logging.jvm.context

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.util.Checks.checkArgument
import io.spine.logging.jvm.util.Checks.checkNotNull
import org.jetbrains.annotations.NotNull
import java.util.ArrayList
import java.util.Arrays

/**
 * Immutable [Metadata] implementation intended for use in nested contexts. Scope metadata can
 * be concatenated to inherit metadata from a parent context. This class is only expected to be
 * needed by implementations of [ScopedLoggingContext] and should not be considered a stable
 * API.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ContextMetadata.java)
 * for historical context.
 */
public abstract class ContextMetadata protected constructor() : Metadata() {

    internal class Entry<T : Any>(
        val key: MetadataKey<T>,
        val value: T
    ) {
        init {
            checkNotNull(key, "key")
            checkNotNull(value, "value")
        }
    }

    /**
     * A builder to collect metadata key/values pairs in order. This class is only expected to be
     * needed by implementations of [ScopedLoggingContext] and should not be considered a stable
     * API.
     */
    public class Builder {

        // Set an explicitly small initial capacity to avoid excessive allocations when we only ever
        // expect one or two keys to be added per context.
        // We don't optimize for the case of zero keys, since the scoped context builder
        // shouldn't create a builder until the first key is added.
        private val entries = ArrayList<Entry<*>>(2)

        /** Add a single metadata key/value pair to the builder. */
        @CanIgnoreReturnValue
        public fun <T : Any> add(key: MetadataKey<T>, value: T): Builder {
            // Entries are immutable and get moved into the metadata when it is built,
            // so these get shared and reduce the size of the metadata storage compared
            // to storing adjacent key/value pairs.
            entries.add(Entry(key, value))
            return this
        }

        public fun build(): ContextMetadata {
            // Analysis shows it's quicker to pass an empty array here and let the JVM optimize to
            // avoid creating an empty array just to overwrite all its elements.
            return ImmutableScopeMetadata(entries.toArray(EMPTY_ARRAY))
        }

        private companion object {
            private val EMPTY_ARRAY = arrayOfNulls<Entry<*>>(0)
        }
    }

    /**
     * Concatenates the given context metadata *after* this instance. Key value pairs are
     * simply concatenated (rather than being merged) which may result in multiple single valued
     * keys existing in the resulting sequence.
     *
     * Whether this is achieved via copying or chaining of instances is an implementation
     * detail.
     *
     * Use [io.spine.logging.jvm.backend.MetadataProcessor] to process
     * metadata consistently with respect to single valued and repeated keys, and use
     * [Metadata.findValue] to look up the "most recent" value for a single
     * valued key.
     */
    public abstract fun concatenate(metadata: ContextMetadata): ContextMetadata

    // Internal method to deal in entries directly during concatenation.
    internal abstract fun get(n: Int): Entry<*>

    @Suppress("UNCHECKED_CAST")
    override fun getKey(n: Int): @NotNull MetadataKey<@NotNull Any> {
        return get(n).key as MetadataKey<Any>
    }

    override fun getValue(n: Int): Any {
        return get(n).value
    }

    private class ImmutableScopeMetadata(private val entries: Array<Entry<*>?>) : ContextMetadata() {

        override fun size(): Int {
            return entries.size
        }

        override fun get(n: Int): Entry<*> {
            return entries[n]!!
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> findValue(key: MetadataKey<T>): T? {
            checkCannotRepeat(key)
            for (n in entries.indices.reversed()) {
                val e = entries[n]!!
                if (e.key == key) {
                    return e.value as T
                }
            }
            return null
        }

        override fun concatenate(metadata: ContextMetadata): ContextMetadata {
            val extraSize = metadata.size()
            if (extraSize == 0) {
                return this
            }
            if (entries.isEmpty()) {
                return metadata
            }
            val merged = Arrays.copyOf(entries, entries.size + extraSize)
            for (i in 0 until extraSize) {
                merged[i + entries.size] = metadata.get(i)
            }
            return ImmutableScopeMetadata(merged)
        }
    }

    private class SingletonMetadata<T : Any>(key: MetadataKey<T>, value: T) : ContextMetadata() {

        private val entry = Entry(key, value)

        override fun size(): Int {
            return 1
        }

        override fun get(n: Int): Entry<*> {
            if (n == 0) {
                return entry
            }
            throw IndexOutOfBoundsException(n.toString())
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> findValue(key: MetadataKey<T>): T? {
            checkCannotRepeat(key)
            return if (entry.key == key) entry.value as T else null
        }

        override fun concatenate(metadata: ContextMetadata): ContextMetadata {
            // No check for size() == 0 since this instance always has one value.
            val extraSize = metadata.size()
            if (extraSize == 0) {
                return this
            }
            val merged = arrayOfNulls<Entry<*>>(extraSize + 1)
            merged[0] = entry
            for (i in 0 until extraSize) {
                merged[i + 1] = metadata.get(i)
            }
            return ImmutableScopeMetadata(merged)
        }
    }

    /**
     * This is a static nested class as opposed to an anonymous class assigned to a constant field
     * to decouple its classloading when Metadata is loaded.
     *
     * Android users are particularly careful about unnecessary class loading,
     * and we've used similar mechanisms in Guava (see CharMatchers).
     */
    private class EmptyMetadata private constructor() : ContextMetadata() {

        override fun size(): Int {
            return 0
        }

        override fun get(n: Int): Entry<*> {
            throw IndexOutOfBoundsException(n.toString())
        }

        override fun <T : Any> findValue(key: MetadataKey<T>): T? {
            // For consistency, do the same checks as for non-empty instances.
            checkCannotRepeat(key)
            return null
        }

        override fun concatenate(metadata: ContextMetadata): ContextMetadata {
            return metadata
        }

        companion object {
            val INSTANCE = EmptyMetadata()
        }
    }

    public companion object {
        /** Returns a new [ContextMetadata] builder. */
        @JvmStatic
        public fun builder(): Builder {
            return Builder()
        }

        /** Returns a space efficient [ContextMetadata] containing a single value. */
        @JvmStatic
        public fun <T : Any> singleton(key: MetadataKey<T>, value: T): ContextMetadata {
            return SingletonMetadata(key, value)
        }

        /** Returns the empty [ContextMetadata]. */
        // We can't use empty() here as that's already taken by Metadata.
        @JvmStatic
        public fun none(): ContextMetadata {
            return EmptyMetadata.INSTANCE
        }

        private fun <T : Any> checkCannotRepeat(key: MetadataKey<T>) {
            checkArgument(!key.canRepeat(), "metadata key must be single valued")
        }
    }
}
