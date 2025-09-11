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

package io.spine.logging.context

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.util.Checks.checkMetadataIdentifier

/**
 * A simple immutable implementation of [Map.Entry].
 */
private data class SimpleEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>

/**
 * Immutable tags which can be attached to log statements via
 * platform-specific injection mechanisms.
 *
 * A tag is either a "simple" tag, added via [Builder.addTag] or a tag with a
 * value, added via one of the `addTag(name,value)` methods. When thinking of tags as a
 * `Map<String, Set<Object>>`, the value of a "simple" tag is the empty set.
 *
 * Tag values can be of several simple types and are held in a stable, sorted order within a
 * `Tags` instance. In other words it never matters in which order two `Tags` instances
 * are merged.
 *
 * When tags are merged, the result is the union of the values. This is easier to explain When
 * thinking of tags as a `Map<String, Set<Object>>`, where "merging" means taking the union of
 * the `Set` associated with the tag name. In particular, for a given tag name:
 *
 * - Adding the same value for a given tag twice has no additional effect.
 * - Adding a simple tag twice has no additional effect.
 * - Adding a tag with a value is also implicitly like adding a simple tag with the same name.
 *
 * The [toString] implementation of this class provides a human-readable, machine-parsable
 * representation of the tags.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/Tags.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public class Tags private constructor(private val map: LightweightTagMap) {

    public companion object {

        private val emptyTags = Tags(LightweightTagMap(emptyList()))

        /**
         * Returns a new builder for adding tags.
         */
        @JvmStatic
        public fun builder(): Builder = Builder()

        /**
         * Returns the immutable empty tags instance.
         */
        @JvmStatic
        public fun empty(): Tags = emptyTags

        /**
         * Returns a single tag without needing to use the builder API.
         *
         * Where multiple tags are needed, it is always better to use the builder directly.
         */
        @JvmStatic
        public fun of(name: String, value: String): Tags = Tags(name, value)

        /**
         * Returns a single tag without needing to use the builder API.
         *
         * Where multiple tags are needed, it is always better to use the builder directly.
         */
        @JvmStatic
        public fun of(name: String, value: Boolean): Tags = Tags(name, value)

        /**
         * Returns a single tag without needing to use the builder API.
         *
         * Where multiple tags are needed, it is always better to use the builder directly.
         */
        @JvmStatic
        public fun of(name: String, value: Long): Tags = Tags(name, value)

        /**
         * Returns a single tag without needing to use the builder API.
         *
         * Where multiple tags are needed, it is always better to use the builder directly.
         */
        @JvmStatic
        public fun of(name: String, value: Double): Tags = Tags(name, value)
    }

    /**
     * Called for singleton `Tags` instances (but we need to check arguments here).
     */
    private constructor(name: String, value: Any) : this(
        LightweightTagMap(checkMetadataIdentifier(name), value)
    )

    /**
     * A mutable builder for tags.
     */
    public class Builder {

        private val keyValuePairs = ArrayList<KeyValuePair>()

        /**
         * Adds an empty tag, ensuring that the given name exists in the tag map with
         * at least an empty set of values.
         *
         * Adding the same name more than once has no effect.
         *
         * When viewed as a `Set`, the value for an empty tag is just the empty set.
         * However, if other values are added for the same name, the set of values will no
         * longer be empty and the call to [addTag] will have had no lasting effect.
         */
        @CanIgnoreReturnValue
        public fun addTag(name: String): Builder =
            addImpl(name, null)

        /**
         * Adds a string value for the given name, ensuring that the values for
         * the given name contain at least this value.
         *
         * Adding the same name/value pair more than once has no effect.
         */
        @CanIgnoreReturnValue
        public fun addTag(name: String, value: String): Builder =
            addImpl(name, value)

        /**
         * Adds a boolean value for the given name, ensuring that the values for
         * the given name contain at least this value.
         *
         * Adding the same name/value pair more than once has no effect.
         */
        @CanIgnoreReturnValue
        public fun addTag(name: String, value: Boolean): Builder =
            addImpl(name, value)

        /**
         * Adds a long value for the given name, ensuring that the values for
         * the given name contain at least this value.
         *
         * Adding the same name/value pair more than once has no effect.
         *
         * Note however that for numeric values, differing types (long/double) are always
         * considered distinct, so invoking both `addTag("foo", 1234L)` and
         * `addTag("foo", 1234.0D)` will result in two values for the tag.
         */
        @CanIgnoreReturnValue
        public fun addTag(name: String, value: Long): Builder =
            addImpl(name, value)

        /**
         * Adds a double value for the given name, ensuring that the values for the given name
         * contain at least this value. Adding the same name/value pair more than once has no effect.
         *
         * Note however that for numeric values, differing types (long/double) are always
         * considered distinct, so invoking both `addTag("foo", 1234L)` and
         * `addTag("foo", 1234.0D)` will result in two values for the tag.
         */
        @CanIgnoreReturnValue
        public fun addTag(name: String, value: Double): Builder =
            addImpl(name, value)

        @CanIgnoreReturnValue
        private fun addImpl(name: String, value: Any?): Builder {
            keyValuePairs.add(KeyValuePair(checkMetadataIdentifier(name), value))
            return this
        }

        /**
         * Returns an immutable tags instance.
         */
        public fun build(): Tags {
            if (keyValuePairs.isEmpty()) {
                return emptyTags
            }
            // Safe, even for a reused builder, because we never care
            // about the original value order.
            // We/ could deduplicate here to guard against pathological use,
            // but it should never matter.
            keyValuePairs.sort()
            return Tags(LightweightTagMap(keyValuePairs))
        }

        override fun toString(): String = build().toString()
    }

    /**
     * Returns an immutable map containing the tag values.
     */
    public fun asMap(): Map<String, Set<Any?>> = map

    /**
     * Returns whether this instance is empty.
     */
    public fun isEmpty(): Boolean =
        // In theory, only the `EMPTY_TAGS` instance will ever be empty,
        // but this check is not expensive.
        map.isEmpty()

    /**
     * Merges two tags instances, combining values for any name contained in both.
     */
    @Suppress("ReturnCount")
    public fun merge(other: Tags): Tags {
        if (other.isEmpty()) {
            return this
        }
        if (this.isEmpty()) {
            return other
        }
        // We could check if they are equal or one is a subset of the other,
        // but we *really* do not expect that to be a common situation and
        // merging should be fast enough.
        return Tags(LightweightTagMap(map, other.map))
    }

    override fun equals(other: Any?): Boolean =
        (other is Tags) && other.map == map

    /** Inverts the bits in the map hashcode just to be different. */
    override fun hashCode(): Int =
        map.hashCode().inv()

    /**
     * Returns human-readable representation of the tags.
     *
     * This is not a stable representation and may change over time.
     * If you need to format tags reliably for logging, you should not
     * rely on this method.
     */
    override fun toString(): String =
        map.toString()
}

private data class KeyValuePair(
    val key: String,
    val value: Any?
) : Comparable<KeyValuePair> {

    override fun compareTo(other: KeyValuePair): Int {
        var signum = key.compareTo(other.key)
        if (signum == 0) {
            signum = when {
                value != null && other.value != null ->
                    ValueComparator.compare(value, other.value)
                value != null -> 1
                other.value != null -> -1
                else -> 0
            }
        }
        return signum
    }
}

/**
 * Allowed types of tag values.
 *
 * This ensures that tag values have well-known semantics and can
 * always be formatted in a clearly and unambiguously.
 *
 * The ordering of elements in this enum should not change as it defines the sort order between
 * values of different types. New elements need not be added at the end though.
 */
private enum class Type {

    BOOLEAN {
        override fun compare(lhs: Any, rhs: Any): Int =
            (lhs as Boolean).compareTo(rhs as Boolean)
    },
    STRING {
        override fun compare(lhs: Any, rhs: Any): Int =
            (lhs as String).compareTo(rhs as String)
    },
    LONG {
        override fun compare(lhs: Any, rhs: Any): Int =
            (lhs as Long).compareTo(rhs as Long)
    },
    DOUBLE {
        override fun compare(lhs: Any, rhs: Any): Int =
            (lhs as Double).compareTo(rhs as Double)
    };

    abstract fun compare(lhs: Any, rhs: Any): Int

    companion object {

        fun of(tag: Any): Type {
            // There should be exactly as many public methods to set tag values as
            // there are cases here.
            return when (tag) {
                is String -> STRING
                is Boolean -> BOOLEAN
                is Long -> LONG
                is Double -> DOUBLE
                else -> {
                    // Should never happen because only known types can be
                    // passed via public methods.
                    throw AssertionError("invalid tag type: ${tag.javaClass}")
                }
            }
        }
    }
}

/**
 * Compares two values by their type, and then by values themselves.
 */
private object ValueComparator : Comparator<Any> {

    override fun compare(left: Any, right: Any): Int {
        val ltype = Type.of(left)
        val rtype = Type.of(right)
        return if (ltype == rtype) {
            ltype.compare(left, right)
        } else {
            ltype.compareTo(rtype)
        }
    }
}

/*
 * A super lightweight, immutable multi-map to hold tag values. The implementation packs all
 * entries and values into a single array, and uses an offset array to jump to the start of each
 * set. Type safety is ensured by careful partitioning during construction of the array.
 *
 * The total allocations for a Tags instance are:
 * 1 x array for entries and values (no duplication, size of map + size of all value sets)
 * 1 x array for offsets (size of the map)
 * N x entries which hold 2 field each (N = size of map)
 * 1 x entry set (holds 1 field)
 *
 * It is about 6 x 32-bits per entry (including object headers) and an extra 32 bits per value.
 * For the largest normal use cases where you have up to 10 values in the tags, one per key,
 * this is under 300 bytes.
 *
 * Previously, using a `TreeMap<String, TreeSet<Object>>`, it was in the region of 12 x 32 bits
 * per entry, and an additional 8 x 32 bits per value (based on examining the source for
 * `TreeSet` and `TreeMap`), giving a rough estimate of at least 800 bytes.
 */
@Suppress("TooManyFunctions")
private class LightweightTagMap : AbstractMap<String, Set<Any?>> {

    companion object {

        /**
         * A heuristic used when deciding to resize element or offset arrays.
         *
         * Arrays above this size will give savings when resized by more than 10%.
         * In this code, the maximum saving is 50% of the array size, so arrays at or
         * below this limit could only be wasting at most half this value of elements.
         */
        private const val SMALL_ARRAY_LENGTH = 16

        /**
         * A singleton map always has the same immutable offsets (start/end value indices).
        */
        private val singletonOffsets = intArrayOf(1, 2)
    }

    /**
     * This array holds ordered entries followed by values for each entry (grouped by key in order).
     *
     * The offsets array holds the starting offset to each contiguous group of values, plus a final
     * offset to the end of the last group (but we allow sloppy array sizing, so there might be
     * unused elements after the end of the last group, and the array size is not to be trusted).
     *
     * ```
     * [ E(0) ... E(n-1) , V(0,0), V(0,1) ... , V(1,0), V(1,1) ... V(n-1,0), V(n-1,1) ... xxx ... ]
     * offsets --------[0]-^ ---------------[1]-^ --- ... ---[n-1]-^ -----------------[n]-^
     *
     * E(n) = n-th entry, V(n,m) = m-th value for n-th entry.
     * ```
     *
     * The set of entries has index -1, and entries start at 0 and end at `offsets[0]`.
     *
     * For an entry with index `n >= 0`, the values start at `offsets[n]` and end at `offsets[n+1]`.
     * It is permitted to have zero values for an entry (i.e., `offsets(n) == offsets(n+1)`).
     */
    private val array: Array<Any?>

    private val offsets: IntArray

    /**
     * Reusable, immutable entry set.
     *
     * Index -1 is a slightly special case, see `getStart()` etc.
     */
    private val entrySet: Set<Map.Entry<String, Set<Any?>>> =
        SortedArraySet(-1)

    /**
     * Cache these if anyone needs them (not likely in normal usage).
     */
    private var hashCode: Int? = null
    private var toString: String? = null

    /**
     * Singleton constructor
     */
    constructor(name: String, value: Any) {
        this.offsets = singletonOffsets
        this.array = arrayOf(newEntry(name, 0), value)
    }

    /**
     * General constructor.
     */
    constructor(sortedPairs: List<KeyValuePair>) {
        // Allocate the maximum required space for entries and values. This is a bit wasteful if there
        // are pairs with null values in (rare) or duplicates (very rare) but we might resize later.
        val entryCount = countMapEntries(sortedPairs)
        val array = arrayOfNulls<Any>(entryCount + sortedPairs.size)
        val offsets = IntArray(entryCount + 1)

        val totalElementCount = makeTagMap(sortedPairs, entryCount, array, offsets)
        this.array = maybeResizeElementArray(array, totalElementCount)
        this.offsets = offsets
    }

    /**
     * Merging constructor.
     */
    constructor(lhs: LightweightTagMap, rhs: LightweightTagMap) {
        // We already checked that neither map is empty and it's probably not worth
        // optimizing for the case where one is a subset of the other
        // (by the time you've checked you might as well have just made a new instance anyway).
        //
        // We expect to efficiently use most or all of this array (resizing should be rare).
        //
        val maxEntryCount = lhs.size + rhs.size
        val array = arrayOfNulls<Any>(lhs.getTotalElementCount() + rhs.getTotalElementCount())
        val offsets = IntArray(maxEntryCount + 1)

        val totalElementCount = mergeTagMaps(lhs, rhs, maxEntryCount, array, offsets)
        this.array = adjustOffsetsAndMaybeResize(array, offsets, totalElementCount)
        this.offsets = maybeResizeOffsetsArray(offsets)
    }

    // ---- Helpers for making a tag map from the builder. ----

    /**
     * Count the unique keys for a sorted list of key-value pairs.
     */
    private fun countMapEntries(sortedPairs: List<KeyValuePair>): Int {
        var key: String? = null
        var count = 0
        for (pair in sortedPairs) {
            if (pair.key != key) {
                key = pair.key
                count++
            }
        }
        return count
    }

    /**
     * Processes a sorted sequence of key/value pairs to fill the given arrays/offsets.
     *
     * This is a single pass of the pairs.
     */
    private fun makeTagMap(
        sortedPairs: List<KeyValuePair>,
        entryCount: Int,
        array: Array<Any?>,
        offsets: IntArray
    ): Int {
        var key: String? = null
        var value: Any? = null
        var newEntryIndex = 0
        var valueStart = entryCount
        for (pair in sortedPairs) {
            if (pair.key != key) {
                key = pair.key
                array[newEntryIndex] = newEntry(key, newEntryIndex)
                offsets[newEntryIndex] = valueStart
                newEntryIndex++
                value = null
            }
            if (pair.value != null && pair.value != value) {
                value = pair.value
                array[valueStart++] = value
            }
        }
        // If someone was using the builder concurrently, all bets are off.
        if (newEntryIndex != entryCount) {
            throw ConcurrentModificationException("corrupted tag map")
        }
        offsets[entryCount] = valueStart
        return valueStart
    }

    // ---- Helpers for merging tag maps. ----

    @Suppress("ReturnCount")
    private fun mergeTagMaps(
        lhs: LightweightTagMap,
        rhs: LightweightTagMap,
        maxEntryCount: Int,
        array: Array<Any?>,
        offsets: IntArray
    ): Int {
        // Merge values starting at the first safe offset after the largest possible number of
        // entries. We may need to copy elements later to remove any gap due to duplicate keys.
        // If the values are copied down we must remember to re-adjust the offsets as well.
        var valueStart = maxEntryCount
        // The first offset is the start of the first values segment.
        offsets[0] = valueStart

        // We must have at least one entry per map, but they can be null once we run out.
        var lhsEntryIndex = 0
        var lhsEntry = lhs.getEntryOrNull(lhsEntryIndex)
        var rhsEntryIndex = 0
        var rhsEntry = rhs.getEntryOrNull(rhsEntryIndex)

        var newEntryIndex = 0
        while (lhsEntry != null || rhsEntry != null) {
            // Nulls count as being *bigger* than anything (since they indicate the end of the array).
            var signum = when {
                lhsEntry == null -> 1
                rhsEntry == null -> -1
                else -> 0
            }
            if (signum == 0) {
                // Both entries exist and must be compared.
                signum = lhsEntry!!.key.compareTo(rhsEntry!!.key)
                if (signum == 0) {
                    // Merge values, update both indices/entries.
                    array[newEntryIndex] = newEntry(lhsEntry.key, newEntryIndex)
                    newEntryIndex++
                    valueStart = mergeValues(lhsEntry.value, rhsEntry.value, array, valueStart)
                    offsets[newEntryIndex] = valueStart
                    lhsEntry = lhs.getEntryOrNull(++lhsEntryIndex)
                    rhsEntry = rhs.getEntryOrNull(++rhsEntryIndex)
                    continue
                }
            }
            // Signum is non-zero and indicates which entry to process next (without merging).
            if (signum < 0) {
                valueStart =
                    copyEntryAndValues(lhsEntry!!, newEntryIndex++, valueStart, array, offsets)
                lhsEntry = lhs.getEntryOrNull(++lhsEntryIndex)
            } else {
                valueStart =
                    copyEntryAndValues(rhsEntry!!, newEntryIndex++, valueStart, array, offsets)
                rhsEntry = rhs.getEntryOrNull(++rhsEntryIndex)
            }
        }
        return newEntryIndex
    }

    /**
     * Called when merging maps to merge the values for a pair of entries with duplicate keys.
     */
    private fun mergeValues(
        lhs: SortedArraySet<*>,
        rhs: SortedArraySet<*>,
        array: Array<Any?>,
        valueStart: Int
    ): Int {
        // The indexes here are the value indexes within the lhs/rhs elements,
        // not the indexes of the elements in their respective maps, but the basic
        // loop structure is very similar.
        var lhsIndex = 0
        var rhsIndex = 0
        var currentValueStart = valueStart
        while (lhsIndex < lhs.size || rhsIndex < rhs.size) {
            var signum = when {
                lhsIndex == lhs.size -> 1
                rhsIndex == rhs.size -> -1
                else -> 0
            }
            if (signum == 0) {
                signum = ValueComparator.compare(lhs.getValue(lhsIndex), rhs.getValue(rhsIndex))
            }
            // Signum can be zero here for duplicate values
            // (unlike the entry processing loop above).
            val value: Any = when {
                signum < 0 -> lhs.getValue(lhsIndex++)
                else -> {
                    val rhsValue = rhs.getValue(rhsIndex++)
                    if (signum == 0) {
                        // Equal values means we just drop the duplicate.
                        lhsIndex++
                    }
                    rhsValue
                }
            }
            array[currentValueStart++] = value
        }
        return currentValueStart
    }

    // Called when merging maps to copy an entry with a unique key, and all its values.
    private fun copyEntryAndValues(
        entry: Map.Entry<String, SortedArraySet<Any?>>,
        entryIndex: Int,
        valueStart: Int,
        array: Array<Any?>,
        offsets: IntArray
    ): Int {
        val values = entry.value
        val valueCount = values.getEnd() - values.getStart()
        System.arraycopy(values.getValuesArray(), values.getStart(), array, valueStart, valueCount)
        array[entryIndex] = newEntry(entry.key, entryIndex)
        // Record the end offset for the segment, and return it as the start of the next segment.
        val valueEnd = valueStart + valueCount
        offsets[entryIndex + 1] = valueEnd
        return valueEnd
    }

    /**
     * Called after merging two maps to see if the offset array needs adjusting.
     * This method may also "right size" the values array if it detected sufficient wastage.
     */
    private fun adjustOffsetsAndMaybeResize(
        array: Array<Any?>,
        offsets: IntArray,
        entryCount: Int
    ): Array<Any?> {
        // See if there's a gap between entries and values (due to duplicate keys being merged).
        // If not then we know that the array uses all its elements (since no values were merged).
        val maxEntries = offsets[0]
        val offsetReduction = maxEntries - entryCount
        if (offsetReduction == 0) {
            return array
        }
        for (i in 0..entryCount) {
            offsets[i] -= offsetReduction
        }
        var dstArray = array
        val totalElementCount = offsets[entryCount]
        val valueCount = totalElementCount - entryCount
        if (mustResize(array.size, totalElementCount)) {
            dstArray = arrayOfNulls(totalElementCount)
            System.arraycopy(array, 0, dstArray, 0, entryCount)
        }
        // If we are reusing the working array, this copy leaves non-null values in the unused
        // portion at the end of the array, but these references are also repeated earlier in the
        // array, so there's no issue with leaking any values because of this.
        System.arraycopy(array, maxEntries, dstArray, entryCount, valueCount)
        return dstArray
    }

    /**
     * Resize the value array if necessary.
     */
    private fun maybeResizeElementArray(array: Array<Any?>, bestLength: Int): Array<Any?> {
        return if (mustResize(array.size, bestLength)) {
            array.copyOf(bestLength)
        } else {
            array
        }
    }

    /**
     * Resize the value array if necessary (separate since int[] and Object[] are not compatible).
     */
    private fun maybeResizeOffsetsArray(offsets: IntArray): IntArray {
        // Remember we must account for the extra final offset (the end of the final segment).
        val bestLength = offsets[0] + 1
        return if (mustResize(offsets.size, bestLength)) {
            offsets.copyOf(bestLength)
        } else {
            offsets
        }
    }

    /**
     * Common logic to decide if we're wasting too much off an array and need to "right size" it.
     *
     * @return `true` if more than 10% wasted in a non-trivial sized array.
     */
    @Suppress("MagicNumber")
    private fun mustResize(actualLength: Int, bestLength: Int): Boolean =
        actualLength > SMALL_ARRAY_LENGTH && (9 * actualLength > 10 * bestLength)

    /**
     * Returns a new entry for this map with the given key and values read according to the
     * specified offset index (see SortedArraySet).
     */
    private fun newEntry(key: String, index: Int): Map.Entry<String, SortedArraySet<Any?>> =
        SimpleEntry(key, SortedArraySet(index))

    @Suppress("UNCHECKED_CAST") // Safe when the index is in range.
    private fun getEntryOrNull(index: Int): Map.Entry<String, SortedArraySet<Any?>>? =
        if (index < offsets[0]) {
            array[index] as Map.Entry<String, SortedArraySet<Any?>>
        } else {
            null
        }

    /**
     * Returns the total number of used elements in the entry/value array. Note that this may well
     * be less than the total array size, since we allow for "sloppy" resizing.
     */
    private fun getTotalElementCount(): Int =
        offsets[size]

    @Suppress("UNCHECKED_CAST")
    override val entries: Set<Map.Entry<String, Set<Any?>>>
        get() = entrySet

    /**
     * A lightweight set based on an range in an array.
     *
     * This assumes (but does not enforce) that the elements in the array are
     * ordered according to the comparator.
     * It uses the array and offsets from the outer map class, needing only
     * to specify its index from which start/end offsets can be derived.
     */
    inner class SortedArraySet<T>(
        // -1 = key set, 0...N-1 = values set.
        private val index: Int
    ) : AbstractSet<T>() {

        fun getValuesArray(): Array<Any?> =
            array

        // Caller must check 0 <= n < size().
        fun getValue(n: Int): Any =
            array[getStart() + n]!!

        fun getStart(): Int =
            if (index == -1) 0 else offsets[index]

        fun getEnd(): Int =
            offsets[index + 1]

        override val size: Int
            get() = getEnd() - getStart()

        override fun contains(element: T): Boolean =
            array.contains(element)

        override fun iterator(): MutableIterator<T> {

            return object : MutableIterator<T> {
                private var n = 0

                override fun hasNext(): Boolean =
                    n < this@SortedArraySet.size

                @Suppress("UNCHECKED_CAST")
                override fun next(): T {
                    // Copy to local variable to guard against concurrent calls to `next()`
                    // causing the index to become corrupted, and going off the end of
                    // the valid range for this iterator.
                    // This doesn't make concurrent iteration thread safe in general,
                    // but prevents weird situations where a value for a different element
                    // could be returned by mistake.
                    val index = n
                    if (index < this@SortedArraySet.size) {
                        val value = array[getStart() + index] as T
                        // Written value is never > size(), even with concurrent iteration.
                        n = index + 1
                        return value
                    }
                    throw NoSuchElementException()
                }

                override fun remove() {
                    throw UnsupportedOperationException()
                }
            }
        }
    }

    override fun hashCode(): Int {
        // Abstract maps cannot cache their hash codes, but we know we're immutable, so we can.
        if (hashCode == null) {
            hashCode = super.hashCode()
        }
        return hashCode!!
    }

    override fun toString(): String {
        if (toString == null) {
            toString = super.toString()
        }
        return toString!!
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as LightweightTagMap

        if (hashCode != other.hashCode) return false
        if (!array.contentEquals(other.array)) return false
        if (!offsets.contentEquals(other.offsets)) return false
        if (entrySet != other.entrySet) return false
        if (toString != other.toString) return false
        if (entries != other.entries) return false

        return true
    }
}
