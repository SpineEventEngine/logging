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

import io.spine.logging.jvm.backend.Metadata;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * A [Metadata] with read/write features.
 */
final class MutableMetadata extends Metadata {

    /**
     * The default number of key/value pairs we initially allocate space for when someone adds
     * metadata to this context.
     *
     * <p>Note: As of 10/12 the VM allocates small object arrays very linearly with respect to
     * the number of elements (an array has a 12 byte header with 4 bytes/element for object
     * references). The allocation size is always rounded up to the next 8 bytes which means we
     * can just pick a small value for the initial size and grow from there without too much
     * waste.
     *
     * <p>For 4 key/value pairs, we will need 8 elements in the array, which will take up 48
     * bytes {@code (12 + (8 * 4) = 44}, which when rounded up is 48.
     */
    private static final int INITIAL_KEY_VALUE_CAPACITY = 4;

    /**
     * The array of key/value pairs to hold any metadata that might be added by the logger or any
     * of the fluent methods on our API. This is an array so it is as space-efficient as possible.
     */
    private Object[] keyValuePairs = new Object[2 * INITIAL_KEY_VALUE_CAPACITY];

    /** The number of key/value pairs currently stored in the array. */
    private int keyValueCount = 0;

    @Override
    public int size() {
        return keyValueCount;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull MetadataKey<@NotNull Object> getKey(int n) {
        if (n >= keyValueCount) {
            throw new IndexOutOfBoundsException(n);
        }
        return (MetadataKey<Object>) keyValuePairs[2 * n];
    }

    @Override
    public Object getValue(int n) {
        if (n >= keyValueCount) {
            throw new IndexOutOfBoundsException(n);
        }
        return keyValuePairs[(2 * n) + 1];
    }

    private int indexOf(MetadataKey<?> key) {
        for (var index = 0; index < keyValueCount; index++) {
            if (keyValuePairs[2 * index].equals(key)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    @Nullable
    public <T> T findValue(MetadataKey<T> key) {
        var index = indexOf(key);
        return index != -1 ? key.cast(keyValuePairs[(2 * index) + 1]) : null;
    }

    /**
     * Adds the key/value pair to the metadata (growing the internal array as necessary). If the
     * key
     * cannot be repeated, and there is already a value for the key in the metadata, then the
     * existing value is replaced, otherwise the value is added at the end of the metadata.
     */
    <T> void addValue(MetadataKey<T> key, T value) {
        if (!key.canRepeat()) {
            var index = indexOf(key);
            if (index != -1) {
                keyValuePairs[(2 * index) + 1] = checkValue(value);
                return;
            }
        }
        // Check that the array is big enough for one more element.
        if (2 * (keyValueCount + 1) > keyValuePairs.length) {
            // Use doubling here (this code should almost never be hit in normal usage and the total
            // number of items should always stay relatively small. If this resizing algorithm is ever
            // modified it is vital that the new value is always an even number.
            keyValuePairs = Arrays.copyOf(keyValuePairs, 2 * keyValuePairs.length);
        }
        keyValuePairs[2 * keyValueCount] = checkKey(key);
        keyValuePairs[(2 * keyValueCount) + 1] = checkValue(value);
        keyValueCount += 1;
    }

    private static <T> @NonNull MetadataKey<T> checkKey(MetadataKey<T> key) {
        return checkNotNull(key, "metadata key");
    }

    private static <T> @NonNull T checkValue(T value) {
        return checkNotNull(value, "metadata value");
    }

    /** Removes all key/value pairs for a given key. */
    @SuppressWarnings("MethodWithMultipleLoops")
    void removeAllValues(MetadataKey<?> key) {
        var index = indexOf(key);
        if (index >= 0) {
            var dest = 2 * index;
            var src = dest + 2;
            while (src < (2 * keyValueCount)) {
                var nextKey = keyValuePairs[src];
                if (!nextKey.equals(key)) {
                    keyValuePairs[dest] = nextKey;
                    keyValuePairs[dest + 1] = keyValuePairs[src + 1];
                    dest += 2;
                }
                src += 2;
            }
            // We know src & dest are +ve and (src > dest), so shifting is safe here.
            keyValueCount -= (src - dest) >> 1;
            while (dest < src) {
                keyValuePairs[dest++] = null;
            }
        }
    }

    /** Strictly for debugging. */
    @Override
    public String toString() {
        var out = new StringBuilder("Metadata{");
        for (var n = 0; n < size(); n++) {
            out.append(" '")
               .append(getKey(n))
               .append("': ")
               .append(getValue(n));
        }
        return out.append(" }")
                  .toString();
    }
}
