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

package io.spine.logging.jvm

import io.spine.logging.jvm.backend.Metadata

/**
 * A [Metadata] with read/write features.
 */
internal class MutableMetadata : Metadata() {

    /**
     * The default number of key/value pairs we initially allocate space for when someone adds
     * metadata to this context.
     *
     * Note: As of 10/12 the VM allocates small object arrays very linearly with
     * respect to the number of elements.
     * An array has a 12-byte header with 4 bytes/element for object references.
     * The allocation size is always rounded up to the next 8 bytes which means we
     * can just pick a small value for the initial size and grow from there without
     * too much waste.
     *
     * For 4 key/value pairs, we will need 8 elements in the array, which will take up
     * 48 bytes `(12 + (8 * 4) = 44`, which when rounded up is 48.
     */
    private companion object {
        private const val INITIAL_KEY_VALUE_CAPACITY = 4
    }

    /**
     * The array of key/value pairs to hold any metadata that might
     * be added by the logger or any of the fluent methods on our API.
     *
     * This is an array, so it is as space-efficient as possible.
     */
    private var keyValuePairs: Array<Any?> = arrayOfNulls(2 * INITIAL_KEY_VALUE_CAPACITY)

    /**
     * The number of key/value pairs currently stored in the array.
     */
    private var keyValueCount = 0

    override fun size(): Int = keyValueCount

    @Suppress("UNCHECKED_CAST")
    override fun getKey(n: Int): MetadataKey<Any> {
        if (n >= keyValueCount) {
            throw IndexOutOfBoundsException(n)
        }
        return keyValuePairs[2 * n] as MetadataKey<Any>
    }

    override fun getValue(n: Int): Any {
        if (n >= keyValueCount) {
            throw IndexOutOfBoundsException(n)
        }
        return keyValuePairs[(2 * n) + 1]!!
    }

    private fun indexOf(key: MetadataKey<*>): Int {
        for (index in 0 until keyValueCount) {
            if (keyValuePairs[2 * index] == key) {
                return index
            }
        }
        return -1
    }

    override fun <T : Any> findValue(key: MetadataKey<T>): T? {
        val index = indexOf(key)
        return if (index != -1) key.cast(keyValuePairs[(2 * index) + 1]) else null
    }

    /**
     * Adds the key/value pair to the metadata (growing the internal array as necessary). If the key
     * cannot be repeated, and there is already a value for the key in the metadata, then the
     * existing value is replaced, otherwise the value is added at the end of the metadata.
     */
    fun <T : Any> addValue(key: MetadataKey<T>, value: T) {
        if (!key.canRepeat()) {
            val index = indexOf(key)
            if (index != -1) {
                keyValuePairs[(2 * index) + 1] = value
                return
            }
        }
        // Check that the array is big enough for one more element.
        if (2 * (keyValueCount + 1) > keyValuePairs.size) {
            // Use doubling here (this code should almost never be hit in normal usage and the total
            // number of items should always stay relatively small. If this resizing algorithm is ever
            // modified it is vital that the new value is always an even number.
            keyValuePairs = keyValuePairs.copyOf(2 * keyValuePairs.size)
        }
        keyValuePairs[2 * keyValueCount] = key
        keyValuePairs[(2 * keyValueCount) + 1] = value
        keyValueCount += 1
    }

    /**
     * Removes all key/value pairs for a given key.
     */
    @Suppress("NestedBlockDepth")
    fun removeAllValues(key: MetadataKey<*>) {
        val index = indexOf(key)
        if (index >= 0) {
            var dest = 2 * index
            var src = dest + 2
            while (src < (2 * keyValueCount)) {
                val nextKey = keyValuePairs[src]
                if (nextKey != key) {
                    keyValuePairs[dest] = nextKey
                    keyValuePairs[dest + 1] = keyValuePairs[src + 1]
                    dest += 2
                }
                src += 2
            }
            // We know src & dest are +ve and (src > dest), so shifting is safe here.
            keyValueCount -= (src - dest) shr 1
            while (dest < src) {
                keyValuePairs[dest++] = null
            }
        }
    }

    /**
     * Strictly for debugging.
     */
    override fun toString(): String = buildString {
        append("Metadata{")
        for (n in 0 until size()) {
            append(" '")
            append(getKey(n))
            append("': ")
            append(getValue(n))
        }
        append(" }")
    }
}
