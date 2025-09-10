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

package io.spine.logging.context

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import kotlin.math.min

/**
 * A fast prefix-Trie implementation for segmented keys.
 *
 * For example, given the mapping:
 * ```
 * "foo" = FOO
 * "foo.bar" = FOO_BAR
 * ```
 * (where `.` is the segment separator) and a default value of `DEFAULT`, we get:
 *
 * - `find("foo") == FOO` (exact match)
 * - `find("foo.bar") == FOO_BAR` (exact match)
 * - `find("foo.foo") == FOO` (nearest parent)
 * - `find("bar") == DEFAULT` (no match)
 *
 * More information:
 * - Supports empty segments and never allocates memory during a lookup.
 * - Allows `null` values, but `null` stops parent matching.
 * - Immutable and thread-safe after creation.
 */
@Immutable
@ThreadSafe
internal abstract class SegmentTrie<T>(private val defaultValue: T) {

    /**
     * Returns the value of the entry which most closely matches the given key.
     */
    abstract fun find(key: String): T

    /**
     * Returns an immutable view of the entries in this Trie.
     */
    abstract fun getEntryMap(): Map<String, T>

    fun getDefaultValue(): T = defaultValue

    companion object {

        /**
         * Returns a prefix Trie for the given mapping, where keys are segmented via
         * the given separator.
         */
        fun <T> create(map: Map<String, T>, separator: Char, defaultValue: T): SegmentTrie<T> {
            return when (map.size) {
                0 -> EmptyTrie(defaultValue)
                1 -> {
                    val entry = map.entries.first()
                    SingletonTrie(entry.key, entry.value, separator, defaultValue)
                }
                else -> SortedTrie(map, separator, defaultValue)
            }
        }
    }
}

/**
 * Trivial implementation for the empty map (always return the default value).
 */
private class EmptyTrie<T>(defaultValue: T) : SegmentTrie<T>(defaultValue) {
    override fun find(key: String): T = getDefaultValue()
    override fun getEntryMap(): Map<String, T> = emptyMap()
}

/**
 * Trivial implementation for a map with one entry.
 */
private class SingletonTrie<T>(
    private val key: String,
    private val value: T,
    private val separator: Char,
    defaultValue: T
) : SegmentTrie<T>(defaultValue) {

    override fun find(key: String): T {
        // Remember that just being a prefix isn't enough, it must match up to the end of a segment.
        return if (key.startsWith(this.key) &&
            (key.length == this.key.length || key[this.key.length] == separator)
        ) {
            value
        } else {
            getDefaultValue()
        }
    }

    override fun getEntryMap(): Map<String, T> = mapOf(key to value)
}

/**
 * General purpose implementation using a custom binary search to reduce
 * repeated re-comparing of keys.
 *
 * Nothing in or called by the "find" method is allowed to allocate any memory.
 */
private class SortedTrie<T>(
    entries: Map<String, T>,
    private val separator: Char,
    defaultValue: T
) : SegmentTrie<T>(defaultValue) {

    private val keys: Array<String>
    private val values: List<T>
    private val parent: IntArray

    init {
        val sorted = entries.toSortedMap()
        keys = sorted.keys.toTypedArray()
        values = ArrayList(sorted.values)
        parent = buildParentMap(keys, separator)
    }

    @Suppress("ReturnCount")
    override fun find(key: String): T {
        val keyLen = key.length

        // Find the left-hand-side bound and get the size of the common prefix.
        var lhsIdx = 0
        var lhsPrefix = prefixCompare(key, keys[lhsIdx], 0)
        if (lhsPrefix == keyLen) return values[lhsIdx]
        if (lhsPrefix < 0) return getDefaultValue()

        var rhsIdx = keys.size - 1
        var rhsPrefix = prefixCompare(key, keys[rhsIdx], 0)
        if (rhsPrefix == keyLen) {
            // If equal, just return the element.
            return values[rhsIdx]
        }
        if (rhsPrefix >= 0) {
            // If the key is before the first element it has no parent.
            return findParent(key, rhsIdx, rhsPrefix)
        }
        // If rhsPrefix is negative, it is the bitwise-NOT of what we want.
        rhsPrefix = rhsPrefix.inv()

        // Binary search: At the top of the loop, lhsPrefix & rhsPrefix are positive.
        while (true) {
            // Determine the pivot index.
            // NOTE: In theory we might be able to improve performance by biasing the pivot index
            // towards the side with the larger common prefix length.
            val midIdx = (lhsIdx + rhsIdx) ushr 1
            if (midIdx == lhsIdx) {
                // No match found: The left-hand-side is the nearest lexicographical entry
                // (but not equal), but we know that if the search key has a parent in the trie,
                // then it must be a parent of this entry (even if this entry is
                // not a direct sibling).
                return findParent(key, lhsIdx, lhsPrefix)
            }
            // Find the prefix length of the pivot value (using the minimum prefix length of the
            // current bounds to limit the work done).
            val midPrefix: Int = prefixCompare(key, keys[midIdx], min(lhsPrefix, rhsPrefix))
            if (keyLen == midPrefix) {
                // If equal, just return the element.
                return values.get(midIdx)
            }
            if (midPrefix >= 0) {
                // key > pivot, so reset left-hand bound
                lhsIdx = midIdx
                lhsPrefix = midPrefix
            } else {
                // key < pivot, so reset right-hand bound
                rhsIdx = midIdx
                rhsPrefix = midPrefix.inv()
            }
        }
    }

    /**
     * Finds the value of the nearest parent of the given key, starting at the element
     * lexicographically preceding the key (but which is not equal to the key).
     *
     * @param k The key whose parent value we wish to find.
     * @param idx The index of the closest matching key in the trie (`k < keys[idx]`).
     * @param len The common prefix length between `k` and `keys[idx]`.
     *
     * @return the value of the nearest parent of `k`.
     */
    private fun findParent(k: String, idx: Int, len: Int): T {
        var i = idx
        while (!isParent(keys[i], k, len)) {
            i = parent[i]
            if (i == -1) return getDefaultValue()
        }
        return values[i]
    }

    /**
     * Determines if a given candidate value `p` is the parent of a key `k`.
     *
     * We know that `p < k` (lexicographically) and (importantly) `p != k`.
     * We also know that `len` is common prefix length.
     *
     * Thus, either:
     * - The common prefix is a strict prefix of k (i.e. `k.length() > len`).
     * - The common prefix is equal to `k`, but `p` must be longer (or else `p == k`).
     *
     * Thus, if `(p.length <= len)` then `(k.length() > p.length())`.
     *
     * @param p The candidate parent key to check.
     * @param k The key whose parent we are looking for.
     * @param len The maximum length of any possible parent of `k`.
     */
    private fun isParent(p: String, k: String, len: Int): Boolean =
        p.length <= len && k[p.length] == separator

    /**
     * Returns the common prefix between two strings, encoding the returned value to indicate
     * lexicographical order. That is:
     *
     * - If `lhs >= rhs`, the returned value is the common prefix length.
     * - If `lhs < rhs`, the returned value is the bitwise-NOT of the common prefix length.
     *
     * This permits the function to be used for both comparison, and for determining the common
     * prefix length (if the returned prefix length is non-negative and equal to `lhs.length()`
     * then `lhs == rhs`).
     *
     * By allowing a known existing lower bound for the prefix length to be provided, this method
     * can skip re-comparing the beginning of values repeatedly when used in a binary search.
     * The given lower bound value is expected to be the result of previous calls this function (or
     * `0`).
     *
     * @param lhs first value to compare.
     * @param rhs second value to compare.
     * @param start a lower bound for the common prefix length of the given keys, which must be
     *        `<= min(lhs.length(), rhs.length())`.
     * @return the common prefix length, encoded to indicate lexicographical ordering.
     */
    private fun prefixCompare(lhs: String, rhs: String, start: Int): Int {
        val len = min(lhs.length, rhs.length)
        for (n in start until len) {
            val diff = lhs[n] - rhs[n]
            if (diff != 0) return if (diff < 0) n.inv() else n
        }
        return if (len < rhs.length) len.inv() else len
    }
    /**
     * Builds an index mapping array `pmap` such that `pmap[idx]` is
     * the index of the parent element of `keys[idx]`, or `-1` if no parent exists.
     */
    private fun buildParentMap(keys: Array<String>, separator: Char): IntArray {
        val pmap = IntArray(keys.size)
        // The first key cannot have a parent.
        pmap[0] = -1
        for (n in 1..<keys.size) {
            // Assume no parent will be found (just makes things a bit easier later).
            pmap[n] = -1
            // Generate each parent key in turn until a match is found.
            var key = keys[n]
            var sidx = key.lastIndexOf(separator)
            while (sidx >= 0) {
                key = key.substring(0, sidx)
                val i = keys.binarySearch(key, 0, n)
                if (i >= 0) {
                    // Match found, so set index and exit.
                    pmap[n] = i
                    break
                }
                sidx = key.lastIndexOf(separator)
            }
        }
        return pmap
    }

    override fun getEntryMap(): Map<String, T> =
        keys.zip(values).toMap()
}
