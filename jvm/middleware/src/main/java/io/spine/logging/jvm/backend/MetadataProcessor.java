/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend;

import io.spine.logging.jvm.JvmMetadataKey;
import io.spine.logging.jvm.LogContext;
import org.jspecify.annotations.Nullable;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.spine.logging.jvm.util.Checks.checkArgument;
import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * Processor combining scope and log-site metadata into a single view. This is necessary when
 * backends wish to combine metadata without incurring the cost of building maps etc. While it is
 * not strictly necessary to use this processor when handling metadata, it is recommended.
 *
 * <p>The expected usage pattern for this class is that:
 *
 * <ol>
 *   <li>The logger backend creates one or more stateless {@link MetadataHandler} instances as
 *       static constants. These should be immutable and thread safe since they include only code.
 *   <li>When handling a log statement, the logger backend generates a {@link MetadataProcessor} in
 *       the logging thread for the current scope and log-site metadata.
 *   <li>The processor can then be repeatedly used to dispatch calls to one or more of the handlers,
 *       potentially with different mutable context instances.
 * </ol>
 *
 * <p>By splitting the various life-cycles (handler, processor, contexts) this approach should help
 * minimize the cost of processing metadata per log statement.
 *
 * <p>Instances of MetadataProcessor are reusable, but not thread safe. All metadata processing must
 * be done in the logging thread.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MetadataProcessor.java">
 *         Original Java code of Google Flogger</a>
 */
public abstract class MetadataProcessor {

    // Immutable empty processor which never handles any metadata.
    private static final MetadataProcessor EMPTY_PROCESSOR = new MetadataProcessor() {
        @Override
        public <C> void process(MetadataHandler<C> handler, C context) {
        }

        @Override
        public <C> void handle(JvmMetadataKey<?> key, MetadataHandler<C> handler, C context) {
        }

        @Override
        public <T> T getSingleValue(JvmMetadataKey<T> key) {
            return null;
        }

        @Override
        public int keyCount() {
            return 0;
        }

        @Override
        public Set<JvmMetadataKey<?>> keySet() {
            return Collections.emptySet();
        }
    };

    /**
     * Returns a new processor for the combined scope and log-site metadata. Note that this returned
     * instance may read directly from the supplied metadata during processing, so the supplied
     * metadata must not be modified while the processor instance is being used.
     *
     * @param scopeMetadata
     *         Metadata for the current scope (i.e., from {@code ScopedLoggingContext})
     * @param logMetadata
     *         Metadata extracted from the current log statement (i.e., from {@code LogData})
     * @return a processor to handle a unified view of the data
     */
    public static MetadataProcessor forScopeAndLogSite(Metadata scopeMetadata,
                                                       Metadata logMetadata) {
        var totalSize = scopeMetadata.size() + logMetadata.size();
        if (totalSize == 0) {
            return EMPTY_PROCESSOR;
        } else if (totalSize <= LightweightProcessor.MAX_LIGHTWEIGHT_ELEMENTS) {
            return getLightweightProcessor(scopeMetadata, logMetadata);
        } else {
            return getSimpleProcessor(scopeMetadata, logMetadata);
        }
    }

    // Visible for testing
    static MetadataProcessor getLightweightProcessor(Metadata scope, Metadata logged) {
        return new LightweightProcessor(scope, logged);
    }

    // Visible for testing
    static MetadataProcessor getSimpleProcessor(Metadata scope, Metadata logged) {
        return new SimpleProcessor(scope, logged);
    }

    private MetadataProcessor() {
    }

    /**
     * Processes a combined view of the scope and log-site metadata in this processor by invoking
     * the
     * given handler for each distinct metadata key. The handler method invoked depends on whether
     * the
     * key is single valued or repeated.
     *
     * <p>Rules for merging scope and log-site metadata are as follows:
     *
     * <ul>
     *   <li>Distinct keys are iterated in the order they were first declared, with scope keys
     *       preceding log-site keys.
     *   <li>For singleton keys, a log-site value replaces any value supplied in the scope.
     *   <li>For repeated keys, all values are collected in declaration order, with scope values
     *       preceding log-site values.
     * </ul>
     *
     * <p>Note that equal or identical repeated values are permitted, and no "deduplication" is
     * performed. This is very much in contrast to the {@link io.spine.logging.jvm.context.Tags
     * Tags} mechanism, which de-duplicates mappings and reorders keys and values to generate a
     * minimal, canonical representation.
     *
     * <p>Furthermore, scope-supplied tags will be a single value in the scope metadata, keyed with
     * the {@link LogContext.Key#TAGS TAGS} key.
     *
     * @param handler
     *         the metadata handler to be called back
     * @param context
     *         arbitrary context instance to be passed into each callback.
     */
    public abstract <C> void process(MetadataHandler<C> handler, C context);

    /**
     * Invokes the given handler for the combined scope and log-site metadata for a specified key.
     * The handler method invoked depends on whether the key is single valued or repeated.
     * If no metadata is present for the given key, the handler is not invoked.
     */
    public abstract <C> void handle(JvmMetadataKey<?> key, MetadataHandler<C> handler, C context);

    /**
     * Returns the unique value for a single valued key, or {@code null} if not present.
     *
     * @throws IllegalArgumentException
     *         if passed a repeatable key (even if that key has one value).
     */
    public abstract <T> T getSingleValue(JvmMetadataKey<T> key);

    /**
     * Returns the number of unique keys represented by this processor. This is the same as the
     * size
     * of {@link #keySet()}, but a separate method to avoid needing to allocate anything just to
     * know
     * the number of keys.
     */
    public abstract int keyCount();

    /**
     * Returns the set of {@link JvmMetadataKey}s known to this processor, in the order in which
     * they will be processed. Note that this implementation is lightweight, but not necessarily
     * performant for things like containment testing.
     */
    public abstract Set<JvmMetadataKey<?>> keySet();

    /*
     * The values in the keyMap array are structured as:
     *     [ bits 31-5 : bitmap of additional repeated indices | bits 4-0 first value index ]
     *
     * There are 27 additional bits for the mask, but since index 0 could never be an "additional"
     * value, the bit-mask indices only need to start from 1, giving a maximum of:
     *    1 (first value index) + 27 (additional repeated indices in mask)
     * indices in total.
     *
     * Obviously this could be extended to a "long", but the bloom filter is only efficient up to
     * about 10-15 elements (and that's a super rare case anyway). At some point it's just not worth
     * trying to squeeze anymore value from this class and the "SimpleProcessor" should be used
     * instead (we might even want to switch before hitting 28 elements depending on performance).
     */
    private static final class LightweightProcessor extends MetadataProcessor {

        private static final int MAX_LIGHTWEIGHT_ELEMENTS = 28;

        private final Metadata scope;
        private final Metadata logged;
        // Mapping of key/value indices for distinct keys (kept in key "encounter" order).
        private final int[] keyMap;
        // Count of unique keys in the keyMap.
        private final int keyCount;

        private LightweightProcessor(Metadata scope, Metadata logged) {
            this.scope = checkNotNull(scope, "scope metadata");
            this.logged = checkNotNull(logged, "logged metadata");
            // We can never have more distinct keys, so this never needs resizing. This should be the
            // only variable sized allocation required by this algorithm. When duplicate keys exist some
            // elements at the end of the array will be unused, but the array is typically small and it is
            // common for all keys to be distinct, so "right sizing" the array wouldn't be worth it.
            var maxKeyCount = scope.size() + logged.size();
            // This should be impossible (outside of tests).
            checkArgument(maxKeyCount <= MAX_LIGHTWEIGHT_ELEMENTS, "metadata size too large");
            this.keyMap = new int[maxKeyCount];
            this.keyCount = prepareKeyMap(keyMap);
        }

        @Override
        public <C> void process(MetadataHandler<C> handler, C context) {
            for (var i = 0; i < keyCount; i++) {
                var n = keyMap[i];
                dispatch(getKey(n & 0x1F), n, handler, context);
            }
        }

        @Override
        public <C> void handle(JvmMetadataKey<?> key, MetadataHandler<C> handler, C context) {
            var index = indexOf(key, keyMap, keyCount);
            if (index >= 0) {
                dispatch(key, keyMap[index], handler, context);
            }
        }

        @Override
        public <T> T getSingleValue(JvmMetadataKey<T> key) {
            checkArgument(!key.canRepeat(), "key must be single valued");
            var index = indexOf(key, keyMap, keyCount);
            // For single keys, the keyMap values are just the value index.
            return (index >= 0) ? key.cast(getValue(keyMap[index])) : null;
        }

        @Override
        public int keyCount() {
            return keyCount;
        }

        @Override
        public Set<JvmMetadataKey<?>> keySet() {
            // We may want to cache this, since it's effectively immutable, but it's also a small and
            // likely short lived instance, so quite possibly not worth it for the cost of another field.
            return new AbstractSet<JvmMetadataKey<?>>() {
                @Override
                public int size() {
                    return keyCount;
                }

                @Override
                public Iterator<JvmMetadataKey<?>> iterator() {
                    return new Iterator<>() {
                        private int i = 0;

                        @Override
                        public boolean hasNext() {
                            return i < keyCount;
                        }

                        @Override
                        public JvmMetadataKey<?> next() {
                            return getKey(keyMap[i++] & 0x1F);
                        }

                        @Override
                        // in case we are on an earlier Java version with no default method for this
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            };
        }

        // Separate method to re-capture the value type.
        private <T, C> void dispatch(JvmMetadataKey<T> key, int n,
                                     MetadataHandler<C> handler, C context) {
            if (!key.canRepeat()) {
                // For single keys, the keyMap values are just the value index.
                handler.handle(key, key.cast(getValue(n)), context);
            } else {
                handler.handleRepeated(key, new ValueIterator<T>(key, n), context);
            }
        }

        // Note that this could be made a reusable instance (reset between callbacks) if we wanted to
        // same a little on allocations. However this is a fixed size instance and repeated keys are
        // a fairly unusual use case.
        private final class ValueIterator<T> implements Iterator<T> {

            private final JvmMetadataKey<T> key;
            private int nextIndex;
            // For repeated keys, the bits 5-32 contain a mask of additional indices (where bit 5
            // implies index 1, since index 0 cannot apply to an additional repeated value).
            private int mask;

            private ValueIterator(JvmMetadataKey<T> key, int valueIndices) {
                this.key = key;
                // Get the first element index (lowest 5 bits, 0-27).
                this.nextIndex = valueIndices & 0x1F;
                // Adjust keymap indices mask so bit-0 represents the index *after* the first element.
                // This adjustment is 5 (rather than the 4 with which indices are encoded) because we are
                // shifting past the first index.
                this.mask = valueIndices >>> (5 + nextIndex);
            }

            @Override
            public boolean hasNext() {
                return nextIndex >= 0;
            }

            @Override
            public T next() {
                var next = key.cast(getValue(nextIndex));
                if (mask != 0) {
                    // Skip the previous value and any "gaps" in the mask to find the new next index.
                    var skip = 1 + Integer.numberOfTrailingZeros(mask);
                    mask >>>= skip;
                    nextIndex += skip;
                } else {
                    // After returning the current value we're done.
                    nextIndex = -1;
                }
                return next;
            }

            @Override // in case we are on an earlier Java version with no default method for this
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }

        // Fill the keyMap array and return the count of distinct keys found.
        private int prepareKeyMap(int[] keyMap) {
            var bloomFilterMask = 0L;
            var count = 0;
            for (var n = 0; n < keyMap.length; n++) {
                var key = getKey(n);
                // Use the bloom filter mask to get a quick true-negative test for whether we've seen this
                // key before. Most keys are distinct and this test is very reliable up to 10-15 keys, so
                // it saves building a HashSet or similar to track the set of unique keys.
                var oldMask = bloomFilterMask;
                bloomFilterMask |= key.getBloomFilterMask();
                if (bloomFilterMask == oldMask) {
                    // Very probably a duplicate key. This is rare compared to distinct keys, but will happen
                    // (e.g. for repeated keys with several values). Now we find the index of the key (since
                    // we need to update that element in the keyMap array). This is a linear search but in
                    // normal usage should happen once or twice over a small set (e.g. 5 distinct elements).
                    // It is still expected to be faster/cheaper than creating and populating a HashSet.
                    //
                    // NOTE: It is impossible to get here if (n == 0) because the key's bloom filter must have
                    // at least one bit set so can never equal the initial mask first time round the loop.
                    var i = indexOf(key, keyMap, count);
                    // If the index is -1, it wasn't actually in the set and this was a false-positive.
                    if (i != -1) {
                        // Definitely duplicate key. The key could still be non-repeating though since it might
                        // appear in both scope and logged metadata exactly once:
                        // * For non-repeating keys, just replace the existing map value with the new index.
                        // * For repeated keys, keep the index in the low 5-bits and set a new bit in the mask.
                        //
                        // Since we can never see (n == 0) here, we encode index 1 at bit 5 (hence "n + 4", not
                        // "n + 5" below). This trick just gives us the ability to store one more index.
                        keyMap[i] = key.canRepeat() ? keyMap[i] | (1 << (n + 4)) : n;
                        continue;
                    }
                }
                // This key is definitely not already in the keyMap, so add it and increment the count.
                keyMap[count++] = n;
            }
            return count;
        }

        // Returns the (unique) index into the keyMap array for the given key.
        private int indexOf(JvmMetadataKey<?> key, int[] keyMap, int count) {
            for (var i = 0; i < count; i++) {
                // Low 5 bits of keyMap values are *always* an index to a valid metadata key.
                if (key.equals(getKey(keyMap[i] & 0x1F))) {
                    return i;
                }
            }
            return -1;
        }

        private JvmMetadataKey<?> getKey(int n) {
            var scopeSize = scope.size();
            return n >= scopeSize ? logged.getKey(n - scopeSize) : scope.getKey(n);
        }

        private Object getValue(int n) {
            var scopeSize = scope.size();
            return n >= scopeSize ? logged.getValue(n - scopeSize) : scope.getValue(n);
        }
    }

    /**
     * Simple version of a metadata processor which allocates "large" data structures. This is
     * needed
     * when a large number of metadata elements need processing. It should behave exactly the same
     * as
     * the "lightweight" processor if the supplied Metadata is correctly behaved and not modified
     * during processing.
     */
    private static final class SimpleProcessor extends MetadataProcessor {

        private final Map<JvmMetadataKey<?>, Object> map;

        private SimpleProcessor(Metadata scope, Metadata logged) {
            var map = new LinkedHashMap<JvmMetadataKey<?>, Object>();
            addTo(map, scope);
            addTo(map, logged);
            // Wrap any repeated value lists to make them unmodifiable (required for correctness).
            for (var e : map.entrySet()) {
                if (e.getKey()
                     .canRepeat()) {
                    e.setValue(Collections.unmodifiableList((List<?>) e.getValue()));
                }
            }
            this.map = Collections.unmodifiableMap(map);
        }

        // Unlike the LightweightProcessor, we copy references from the Metadata eagerly, so can "cast"
        // values to their key-types early, ensuring safe casting when dispatching.
        private static void addTo(Map<JvmMetadataKey<?>, Object> map, Metadata metadata) {
            for (var i = 0; i < metadata.size(); i++) {
                var key = metadata.getKey(i);
                var value = map.get(key);
                if (key.canRepeat()) {
                    @SuppressWarnings("unchecked")
                    var list = (List<Object>) value;
                    if (list == null) {
                        list = new ArrayList<Object>();
                        map.put(key, list);
                    }
                    // Cast value to ensure that "repeated key is MetadataKey<T>" implies "value is List<T>"
                    list.add(key.cast(metadata.getValue(i)));
                } else {
                    // Cast value to ensure that "singleton key is MetadataKey<T>" implies "value is T".
                    map.put(key, key.cast(metadata.getValue(i)));
                }
            }
        }

        @Override
        public <C> void process(MetadataHandler<C> handler, C context) {
            for (var e : map.entrySet()) {
                dispatch(e.getKey(), e.getValue(), handler, context);
            }
        }

        @Override
        public <C> void handle(JvmMetadataKey<?> key, MetadataHandler<C> handler, C context) {
            var value = map.get(key);
            if (value != null) {
                dispatch(key, value, handler, context);
            }
        }

        // It's safe to ignore warnings since single keys are only ever 'T' when added to the map.
        @Override
        @SuppressWarnings("unchecked")
        public <@Nullable T> T getSingleValue(JvmMetadataKey<T> key) {
            checkArgument(!key.canRepeat(), "key must be single valued");
            var value = map.get(key);
            return (value != null) ? (T) value : null;
        }

        @Override
        public int keyCount() {
            return map.size();
        }

        @Override
        public Set<JvmMetadataKey<?>> keySet() {
            return map.keySet();
        }

        // It's safe to ignore warnings here since we know that repeated keys only ever get 'List<T>'
        // and single keys are only ever 'T' when added to the map.
        @SuppressWarnings("unchecked")
        private static <T, C> void dispatch(
                JvmMetadataKey<T> key, Object value, MetadataHandler<C> handler, C context) {
            if (key.canRepeat()) {
                handler.handleRepeated(key, ((List<T>) value).iterator(), context);
            } else {
                handler.handle(key, (T) value, context);
            }
        }
    }
}
