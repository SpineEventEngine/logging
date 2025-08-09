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

@file:Suppress("MaxLineLength")

package io.spine.logging.jvm.backend

import io.spine.annotation.VisibleForTesting
import io.spine.logging.jvm.LogContext
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.backend.LightweightProcessor.Companion.MAX_LIGHTWEIGHT_ELEMENTS
import io.spine.logging.jvm.checkCannotRepeat
import java.util.*

/**
 * Processor combining scope and log-site metadata into a single view.
 *
 * This is necessary when backends wish to combine metadata without incurring
 * the cost of building maps etc. While it is not strictly necessary to use
 * this processor when handling metadata, it is recommended.
 *
 * The expected usage pattern for this class is that:
 *
 * 1. The logger backend creates one or more stateless [MetadataHandler] instances as
 *    static constants. These should be immutable and thread-safe since they include only code.
 * 2. When handling a log statement, the logger backend generates a `MetadataProcessor` in
 *    the logging thread for the current scope and log-site metadata.
 * 3. The processor can then be repeatedly used to dispatch calls to one or more of the handlers,
 *    potentially with different mutable context instances.
 *
 * By splitting the various life-cycles (handler, processor, contexts) this approach should help
 * minimize the cost of processing metadata per log statement.
 *
 * Instances of `MetadataProcessor` are reusable, but not thread-safe.
 * All metadata processing must be done in the logging thread.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MetadataProcessor.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class MetadataProcessor {

    public companion object {

        /**
         * Immutable empty processor which never handles any metadata.
         */
        private val EMPTY_PROCESSOR = NoOpProcessor()

        /**
         * Returns a new processor for the combined scope and log-site metadata.
         *
         * Note that this returned instance may read directly from the supplied metadata during
         * processing, so the supplied metadata must not be modified while the processor instance
         * is being used.
         *
         * @param scopeMetadata Metadata for the current scope
         *        (i.e., from `ScopedLoggingContext`).
         * @param logMetadata Metadata extracted from the current log statement
         *        (i.e., from `LogData`).
         *
         * @return a processor to handle a unified view of the data.
         */
        @JvmStatic
        public fun forScopeAndLogSite(
            scopeMetadata: Metadata,
            logMetadata: Metadata
        ): MetadataProcessor {
            val totalSize = scopeMetadata.size() + logMetadata.size()
            return when {
                totalSize == 0 ->
                    EMPTY_PROCESSOR
                totalSize <= LightweightProcessor.MAX_LIGHTWEIGHT_ELEMENTS ->
                    getLightweightProcessor(scopeMetadata, logMetadata)
                else -> getSimpleProcessor(scopeMetadata, logMetadata)
            }
        }

        @VisibleForTesting
        @JvmStatic
        internal fun getLightweightProcessor(scope: Metadata, logged: Metadata): MetadataProcessor =
            LightweightProcessor(scope, logged)

        @VisibleForTesting
        @JvmStatic
        internal fun getSimpleProcessor(scope: Metadata, logged: Metadata): MetadataProcessor =
            SimpleProcessor(scope, logged)
    }

    /**
     * Processes a combined view of the scope and log-site metadata in this processor by
     * invoking the given handler for each distinct metadata key.
     *
     * The handler method invoked depends on whether the key is single valued or repeated.
     *
     * Rules for merging scope and log-site metadata are as follows:
     *
     * - Distinct keys are iterated in the order they were first declared, with scope keys
     *   preceding log-site keys.
     * - For singleton keys, a log-site value replaces any value supplied in the scope.
     * - For repeated keys, all values are collected in declaration order, with scope values
     *   preceding log-site values.
     *
     * Note that equal or identical repeated values are permitted, and no "deduplication" is
     * performed. This is very much in contrast to the [io.spine.logging.jvm.context.Tags]
     * mechanism, which de-duplicates mappings and reorders keys and values to generate a
     * minimal, canonical representation.
     *
     * Furthermore, scope-supplied tags will be a single value in the scope metadata, keyed with
     * the [LogContext.Key.TAGS] key.
     *
     * @param handler The metadata handler to be called back.
     * @param context Arbitrary context instance to be passed into each callback.
     */
    public abstract fun <C : Any> process(handler: MetadataHandler<C>, context: C)

    /**
     * Invokes the given handler for the combined scope and log-site metadata for a specified key.
     * The handler method invoked depends on whether the key is single valued or repeated.
     * If no metadata is present for the given key, the handler is not invoked.
     */
    public abstract fun <C : Any> handle(
        key: MetadataKey<Any>,
        handler: MetadataHandler<C>,
        context: C
    )

    /**
     * Returns the unique value for a single valued key, or `null` if not present.
     *
     * @throws IllegalArgumentException if passed a repeatable key (even if that key has one value).
     */
    public abstract fun <T : Any> getSingleValue(key: MetadataKey<T>): T?

    /**
     * Returns the number of unique keys represented by this processor.
     *
     * This is the same as the size of [keySet], but a separate method to avoid needing
     * to allocate anything just to know the number of keys.
     */
    public abstract fun keyCount(): Int

    /**
     * Returns the set of [MetadataKey]s known to this processor, in the order in
     * which they will be processed.
     *
     * Note that this implementation is lightweight, but not necessarily
     * performant for things like containment testing.
     */
    public abstract fun keySet(): Set<MetadataKey<*>>
}

/**
 * Immutable no-op processor which never handles any metadata.
 */
private class NoOpProcessor: MetadataProcessor() {

    override fun <C : Any> process(handler: MetadataHandler<C>, context: C) =
        Unit
    override fun <C : Any> handle(key: MetadataKey<Any>, handler: MetadataHandler<C>, context: C) =
        Unit
    override fun <T : Any> getSingleValue(key: MetadataKey<T>): T? = null
    override fun keyCount(): Int = 0
    override fun keySet(): Set<MetadataKey<*>> = Collections.emptySet()
}

/**
 * The metadata processor involved when the number of metadata elements is less or
 * equal to [MAX_LIGHTWEIGHT_ELEMENTS].
 *
 * The values in the [keyMap] array are structured as:
 * ```
 *     [ bits 31-5 : bitmap of additional repeated indices | bits 4-0 first value index ]
 *  ```
 * There are 27 additional bits for the mask, but since index 0 could never be an "additional"
 * value, the bit-mask indexes only need to start from 1, giving a maximum of:
 * ```
 *    1 (first value index) + 27 (additional repeated indices in mask)
 * ```
 * indexes in total.
 *
 * Obviously, this could be extended to a `long`, but the bloom filter is only efficient up to
 * about 10-15 elements (and that's a super rare case anyway).
 *
 * At some point it is just not worth trying to squeeze anymore value from this class, and
 * the [SimpleProcessor] should be used instead (we might even want to switch before hitting
 * 28 elements depending on performance).
 */
@Suppress("TooManyFunctions")
private class LightweightProcessor(
    private val scope: Metadata,
    private val logged: Metadata
) : MetadataProcessor() {

    companion object {
        const val MAX_LIGHTWEIGHT_ELEMENTS = 28
    }

    /**
     * Mapping of key/value indexes for distinct keys (kept in key "encounter" order).
     */
    private val keyMap: IntArray

    /**
     * Count of unique keys in the [keyMap].
     */
    private val keyCount: Int

    init {
        // We can never have more distinct keys, so this never needs resizing.
        // This should be the only variable sized allocation required by this algorithm.
        // When duplicate keys exist some elements at the end of the array will be unused,
        // but the array is typically small and it is common for all keys to be distinct,
        // so "right sizing" the array wouldn't be worth it.
        val maxKeyCount = scope.size() + logged.size()
        // This should be impossible (outside of tests).
        require(maxKeyCount <= MAX_LIGHTWEIGHT_ELEMENTS) {
            "Metadata size too large: $maxKeyCount. The limit is: $MAX_LIGHTWEIGHT_ELEMENTS."
        }
        this.keyMap = IntArray(maxKeyCount)
        this.keyCount = prepareKeyMap(keyMap)
    }

    override fun <C : Any> process(handler: MetadataHandler<C>, context: C) {
        for (i in 0..<keyCount) {
            val n = keyMap[i]
            dispatch(getKey(n and 0x1F), n, handler, context)
        }
    }

    // Separate method to re-capture the value type.
    private fun <T : Any, C : Any> dispatch(
        key: MetadataKey<T>, n: Int,
        handler: MetadataHandler<C>, context: C
    ) {
        if (!key.canRepeat()) {
            // For single keys, the keyMap values are just the value index.
            handler.handle(key, key.cast(getValue(n))!!, context)
        } else {
            handler.handleRepeated(
                key,
                ValueIterator(key, n) as Iterator<T>,
                context
            )
        }
    }

    override fun <C : Any> handle(key: MetadataKey<Any>, handler: MetadataHandler<C>, context: C) {
        val index = indexOf(key, keyMap, keyCount)
        if (index >= 0) {
            val n = keyMap[index]
            val keyIndex = n and 0x1F
            val foundKey: MetadataKey<Any> = getKey(keyIndex)

            if (!foundKey.canRepeat()) {
                // For single keys, the keyMap values are just the value index.
                val value = getValue(n)
                foundKey.cast(value)?.let { typedValue: Any ->
                    handler.handle(foundKey, typedValue, context)
                }
            } else {
                // For repeated keys, we need to collect all values
                val values = collectValues(foundKey, n)
                handler.handleRepeated(foundKey, values.iterator(), context)
            }
        }
    }

    override fun <T : Any> getSingleValue(key: MetadataKey<T>): T? {
        checkCannotRepeat(key)
        val index = indexOf(key, keyMap, keyCount)
        // For single keys, the keyMap values are just the value index.
        return if (index >= 0) key.cast(getValue(keyMap[index])) else null
    }

    override fun keyCount(): Int = keyCount

    override fun keySet(): Set<MetadataKey<*>> {

        // We may want to cache this, since it's effectively immutable,
        // but it's also a small and likely short lived instance,
        // so quite possibly not worth it for the cost of another field.
        return object : AbstractSet<MetadataKey<*>>() {

            override val size: Int
                get() = keyCount

            override fun iterator(): MutableIterator<MetadataKey<*>> {

                return object : MutableIterator<MetadataKey<*>> {
                    private var i = 0

                    override fun hasNext(): Boolean =
                        i < keyCount

                    override fun next(): MetadataKey<*> =
                        getKey(keyMap[i++] and 0x1F)

                    override fun remove(): Unit =
                        throw UnsupportedOperationException()
                }
            }
        }
    }

    // Helper method to collect all values for a repeated key
    @Suppress("MagicNumber")
    private fun <T : Any> collectValues(key: MetadataKey<T>, valueIndices: Int): List<T> {
        val values = ArrayList<T>()

        // Get the first element index (lowest 5 bits, 0-27).
        var nextIndex = valueIndices and 0x1F
        // For repeated keys, the bits 5-32 contain a mask of additional indices.
        var mask = valueIndices ushr (5 + nextIndex)

        // Add the first value
        key.cast(getValue(nextIndex))?.let { values.add(it) }

        // Add any additional values
        while (mask != 0) {
            // Skip the previous value and any "gaps" in the mask to find the new next index.
            val skip = 1 + Integer.numberOfTrailingZeros(mask)
            mask = mask ushr skip
            nextIndex += skip
            key.cast(getValue(nextIndex))?.let { values.add(it) }
        }

        return values
    }

    // Fill the keyMap array and return the count of distinct keys found.
    @Suppress("NestedBlockDepth", "MagicNumber")
    private fun prepareKeyMap(keyMap: IntArray): Int {
        var bloomFilterMask = 0L
        var count = 0
        for (n in 0 until keyMap.size) {
            val key = getKey(n)
            // Use the bloom filter mask to get a quick true-negative test for whether we've seen this
            // key before. Most keys are distinct and this test is very reliable up to 10-15 keys, so
            // it saves building a HashSet or similar to track the set of unique keys.
            val oldMask = bloomFilterMask
            bloomFilterMask = bloomFilterMask or key.bloomFilterMask
            if (bloomFilterMask == oldMask) {
                // Very probably a duplicate key. This is rare compared to distinct keys, but will happen
                // (e.g. for repeated keys with several values). Now we find the index of the key (since
                // we need to update that element in the keyMap array). This is a linear search but in
                // normal usage should happen once or twice over a small set (e.g. 5 distinct elements).
                // It is still expected to be faster/cheaper than creating and populating a HashSet.
                //
                // NOTE: It is impossible to get here if (n == 0) because the key's bloom filter must have
                // at least one bit set so can never equal the initial mask first time round the loop.
                val i = indexOf(key, keyMap, count)
                // If the index is -1, it wasn't actually in the set and this was a false-positive.
                if (i != -1) {
                    // Definitely duplicate key. The key could still be non-repeating though since it might
                    // appear in both scope and logged metadata exactly once:
                    // * For non-repeating keys, just replace the existing map value with the new index.
                    // * For repeated keys, keep the index in the low 5-bits and set a new bit in the mask.
                    //
                    // Since we can never see (n == 0) here, we encode index 1 at bit 5 (hence "n + 4", not
                    // "n + 5" below). This trick just gives us the ability to store one more index.
                    keyMap[i] = if (key.canRepeat()) keyMap[i] or (1 shl (n + 4)) else n
                    continue
                }
            }
            // This key is definitely not already in the keyMap, so add it and increment the count.
            keyMap[count++] = n
        }
        return count
    }

    // Returns the (unique) index into the keyMap array for the given key.
    private fun indexOf(key: MetadataKey<*>, keyMap: IntArray, count: Int): Int {
        for (i in 0 until count) {
            // Low 5 bits of keyMap values are *always* an index to a valid metadata key.
            if (key == getKey(keyMap[i] and 0x1F)) {
                return i
            }
        }
        return -1
    }

    private fun getKey(n: Int): MetadataKey<Any> {
        val scopeSize = scope.size()
        return if (n >= scopeSize) logged.getKey(n - scopeSize) else scope.getKey(n)
    }

    private fun getValue(n: Int): Any {
        val scopeSize = scope.size()
        return if (n >= scopeSize) logged.getValue(n - scopeSize) else scope.getValue(n)
    }

    /**
     * Mutable iterator over the values.
     *
     * ### Implementation note
     * This could be made a reusable instance (reset between callbacks) if we wanted to
     * same a little on allocations. However, this is a fixed size instance, and repeated
     * keys are a fairly unusual use case.
     */
    @Suppress("MagicNumber")
    private inner class ValueIterator<T : Any>(
        private val key: MetadataKey<T>,
        valueIndices: Int
    ) : MutableIterator<T> {
        private var nextIndex: Int

        // For repeated keys, the bits 5-32 contain a mask of additional indices (where bit 5
        // implies index 1, since index 0 cannot apply to an additional repeated value).
        private var mask: Int

        init {
            // Get the first element index (lowest 5 bits, 0-27).
            this.nextIndex = valueIndices and 0x1F
            // Adjust keymap indices mask so bit-0 represents the index *after* the first element.
            // This adjustment is 5 (rather than the 4 with which indices are encoded) because we are
            // shifting past the first index.
            this.mask = valueIndices ushr (5 + nextIndex)
        }

        override fun hasNext(): Boolean {
            return nextIndex >= 0
        }

        override fun next(): T {
            val next = key.cast(getValue(nextIndex))!!
            if (mask != 0) {
                // Skip the previous value and any "gaps" in the mask to find the new next index.
                val skip = 1 + Integer.numberOfTrailingZeros(mask)
                mask = mask ushr skip
                nextIndex += skip
            } else {
                // After returning the current value we're done.
                nextIndex = -1
            }
            return next
        }

        // in case we are on an earlier Java version with no default method for this
        override fun remove() {
            throw java.lang.UnsupportedOperationException()
        }
    }
}

/**
 * A simple version of a metadata processor which allocates "large" data structures.
 *
 * This is needed when a large number of metadata elements need processing.
 * It should behave exactly the same as the [LightweightProcessor] if
 * the supplied [Metadata] is correctly behaved and not modified during processing.
 */
private class SimpleProcessor(scope: Metadata, logged: Metadata) : MetadataProcessor() {

    private val map: Map<MetadataKey<Any>, Any?>

    init {
        val map = LinkedHashMap<MetadataKey<Any>, Any>()
        map.addTo(scope)
        map.addTo(logged)
        // Wrap any repeated value lists to make them unmodifiable (required for correctness).
        for (e in map.entries) {
            if (e.key.canRepeat()) {
                @Suppress("UNCHECKED_CAST")
                e.setValue(Collections.unmodifiableList(e.value as List<*>))
            }
        }
        this.map = Collections.unmodifiableMap(map)
    }

    override fun <C : Any> process(handler: MetadataHandler<C>, context: C) {
        for (e in map.entries) {
            handler.dispatch(e.key, e.value, context)
        }
    }

    override fun <C : Any> handle(key: MetadataKey<Any>, handler: MetadataHandler<C>, context: C) {
        val value: Any? = map[key]
        if (value != null) {
            handler.dispatch(key, value, context)
        }
    }

    // It's safe to ignore warnings since single keys are only ever 'T' when added to the map.
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSingleValue(key: MetadataKey<T>): T? {
        checkCannotRepeat(key)
        val value: Any? = map[key as MetadataKey<Any>]
        return if (value != null) value as T else null
    }

    override fun keyCount(): Int = map.size
    override fun keySet(): Set<MetadataKey<*>> = map.keys
}

/**
 * Adds all key-value pairs from the given [metadata] into the [map].
 *
 * Unlike [LightweightProcessor], this function eagerly copies references from
 * the [Metadata] and casts values to their key-types early, ensuring safe casting
 * when dispatching.
 *
 * For repeatable keys, values are collected into mutable lists. For singleton keys,
 * any existing value is replaced with the new value.
 *
 * @param map The mutable map to add metadata entries to.
 * @param metadata The metadata entries to add.
 */
private fun MutableMap<MetadataKey<Any>, Any>.addTo(metadata: Metadata) {
    for (i in 0 until metadata.size()) {
        val key = metadata.getKey(i)
        val value = this[key]
        if (key.canRepeat()) {
            @Suppress("UNCHECKED_CAST")
            val list = value as? MutableList<Any?>
                ?: ArrayList<Any?>().also { this[key] = it }

            // Cast value to ensure that "repeated key is MetadataKey<T>" implies "value is List<T>"
            list.add(key.cast(metadata.getValue(i)))
        } else {
            // Cast value to ensure that "singleton key is MetadataKey<T>" implies "value is T".
            this[key] = key.cast(metadata.getValue(i))!!
        }
    }
}

/**
 * Dispatches metadata key-value pairs to the appropriate handler method based on the key type.
 *
 * This extension function routes metadata entries to either [MetadataHandler.handle] for single
 * values or [MetadataHandler.handleRepeated] for repeated values.
 * It performs the necessary type casting based on whether the key is repeatable or not.
 *
 * The function assumes that:
 * - For repeatable keys, the value is always a `List<T>`
 * - For single keys, the value is always of type `T`
 * These invariants are maintained by [MutableMap.addTo].
 *
 * @param T The type of values associated with the metadata key
 * @param C The context type used by the metadata handler.
 * @param key The metadata key being processed.
 * @param value The value associated with the key (may be `null`).
 * @param context The handler context.
 */
private fun <T : Any, C : Any> MetadataHandler<C>.dispatch(
    key: MetadataKey<T>,
    value: Any?,
    context: C
) {
    if (key.canRepeat()) {
        @Suppress("UNCHECKED_CAST")
        handleRepeated(key, (value as MutableList<T>).iterator(), context)
    } else {
        @Suppress("UNCHECKED_CAST")
        handle(key, (value as T), context)
    }
}
