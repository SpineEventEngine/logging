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

package io.spine.logging.jvm.context;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.logging.jvm.MetadataKey;
import io.spine.logging.jvm.backend.Metadata;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.spine.logging.jvm.util.Checks.checkArgument;
import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * Immutable {@link Metadata} implementation intended for use in nested contexts. Scope metadata can
 * be concatenated to inherit metadata from a parent context. This class is only expected to be
 * needed by implementations of {@link ScopedLoggingContext} and should not be considered a stable
 * API.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ContextMetadata.java">
 *         Original Java code of Google Flogger</a> for historical context.
 */
public abstract class ContextMetadata extends Metadata {

    private static final class Entry<T> {

        final MetadataKey<T> key;
        final T value;

        Entry(MetadataKey<T> key, T value) {
            this.key = checkNotNull(key, "key");
            this.value = checkNotNull(value, "value");
        }
    }

    /**
     * A builder to collect metadata key/values pairs in order. This class is only expected to be
     * needed by implementations of {@link ScopedLoggingContext} and should not be considered a
     * stable
     * API.
     */
    public static final class Builder {

        private static final Entry<?>[] EMPTY_ARRAY = new Entry<?>[0];

        // Set an explicitly small initial capacity to avoid excessive allocations when we only ever
        // expect one or two keys to be added per context.
        // We don't optimize for the case of zero keys, since the scoped context builder
        // shouldn't create a builder until the first key is added.
        private final List<Entry<?>> entries = new ArrayList<>(2);

        private Builder() {
        }

        /** Add a single metadata key/value pair to the builder. */
        @CanIgnoreReturnValue
        public <T> Builder add(MetadataKey<T> key, T value) {
            // Entries are immutable and get moved into the metadata when it is built,
            // so these get shared and reduce the size of the metadata storage compared
            // to storing adjacent key/value pairs.
            entries.add(new Entry<>(key, value));
            return this;
        }

        public ContextMetadata build() {
            // Analysis shows it's quicker to pass an empty array here and let the JVM optimize to
            // avoid creating an empty array just to overwrite all its elements.
            return new ImmutableScopeMetadata(entries.toArray(EMPTY_ARRAY));
        }
    }

    /** Returns a new {@code ScopeMetadata} builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a space efficient {@code ScopeMetadata} containing a single value. */
    public static <T> ContextMetadata singleton(MetadataKey<T> key, T value) {
        return new SingletonMetadata(key, value);
    }

    /** Returns the empty {@code ScopeMetadata}. */
    // We can't use empty() here as that's already taken by Metadata.
    public static ContextMetadata none() {
        return EmptyMetadata.INSTANCE;
    }

    private ContextMetadata() {
    }

    /**
     * Concatenates the given context metadata <em>after</em> this instance. Key value pairs are
     * simply concatenated (rather than being merged) which may result in multiple single valued
     * keys
     * existing in the resulting sequence.
     *
     * <p>Whether this is achieved via copying or chaining of instances is an implementation
     * detail.
     *
     * <p>Use {@link io.spine.logging.jvm.backend.MetadataProcessor MetadataProcessor} to process
     * metadata consistently with respect to single valued and repeated keys, and use {@link
     * Metadata#findValue(MetadataKey)} to look up the “most recent” value for a single
     * valued key.
     */
    public abstract ContextMetadata concatenate(ContextMetadata metadata);

    // Internal method to deal in entries directly during concatenation.
    abstract Entry<?> get(int n);

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull MetadataKey<@NotNull Object> getKey(int n) {
        return (MetadataKey<Object>) get(n).key;
    }

    @Override
    public Object getValue(int n) {
        return get(n).value;
    }

    private static final class ImmutableScopeMetadata extends ContextMetadata {

        private final Entry<?>[] entries;

        ImmutableScopeMetadata(Entry<?>[] entries) {
            this.entries = entries;
        }

        @Override
        public int size() {
            return entries.length;
        }

        @Override
        Entry<?> get(int n) {
            return entries[n];
        }

        @Override
        @Nullable
        @SuppressWarnings({"unchecked", "LocalVariableNamingConvention"})
        public <T> T findValue(MetadataKey<T> key) {
            checkCannotRepeat(key);
            for (var n = entries.length - 1; n >= 0; n--) {
                var e = entries[n];
                if (e.key.equals(key)) {
                    return (T) e.value;
                }
            }
            return null;
        }


        @Override
        public ContextMetadata concatenate(ContextMetadata metadata) {
            var extraSize = metadata.size();
            if (extraSize == 0) {
                return this;
            }
            if (entries.length == 0) {
                return metadata;
            }
            var merged = Arrays.copyOf(entries, entries.length + extraSize);
            for (var i = 0; i < extraSize; i++) {
                merged[i + entries.length] = metadata.get(i);
            }
            return new ImmutableScopeMetadata(merged);
        }
    }

    private static <T> void checkCannotRepeat(MetadataKey<T> key) {
        checkArgument(!key.canRepeat(), "metadata key must be single valued");
    }

    private static final class SingletonMetadata extends ContextMetadata {

        private final Entry<?> entry;

        private <T> SingletonMetadata(MetadataKey<T> key, T value) {
            this.entry = new Entry<>(key, value);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        Entry<?> get(int n) {
            if (n == 0) {
                return entry;
            }
            throw new IndexOutOfBoundsException(n);
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public <R> R findValue(MetadataKey<R> key) {
            checkCannotRepeat(key);
            return entry.key.equals(key) ? (R) entry.value : null;
        }

        @Override
        public ContextMetadata concatenate(ContextMetadata metadata) {
            // No check for size() == 0 since this instance always has one value.
            var extraSize = metadata.size();
            if (extraSize == 0) {
                return this;
            }
            var merged = new Entry<?>[extraSize + 1];
            merged[0] = entry;
            for (var i = 0; i < extraSize; i++) {
                merged[i + 1] = metadata.get(i);
            }
            return new ImmutableScopeMetadata(merged);
        }
    }

    /**
     * This is a static nested class as opposed to an anonymous class assigned to a constant field
     * to decouple its classloading when Metadata is loaded.
     *
     * <p>Android users are particularly careful about unnecessary class loading,
     * and we've used similar mechanisms in Guava (see CharMatchers).
     */
    private static final class EmptyMetadata extends ContextMetadata {

        static final ContextMetadata INSTANCE = new EmptyMetadata();

        @Override
        public int size() {
            return 0;
        }

        @Override
        Entry<?> get(int n) {
            throw new IndexOutOfBoundsException(n);
        }

        @Override
        @Nullable
        public <T> T findValue(MetadataKey<T> key) {
            // For consistency, do the same checks as for non-empty instances.
            checkCannotRepeat(key);
            return null;
        }

        @Override
        public ContextMetadata concatenate(ContextMetadata metadata) {
            return metadata;
        }
    }
}
