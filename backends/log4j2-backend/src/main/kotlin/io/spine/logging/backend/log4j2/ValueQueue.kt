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

package io.spine.logging.backend.log4j2

import io.spine.logging.KeyValueHandler
import io.spine.logging.context.Tags
import io.spine.logging.util.Checks.checkNotNull
import java.util.LinkedList

/**
 * A simple FIFO queue linked-list implementation designed to store multiple
 * metadata values in a org.apache.logging.log4j.util.StringMap.
 *
 * There are two aspects worth pointing out:
 *
 * 1) It is expected that a value queue always contains at least a single item.
 *    You cannot add null references to the queue, and you cannot create an empty queue.
 *
 * 2) It is expected to access the contents of the value queue via an iterator only.
 *    Hence, we do not provide a method for taking the first item in the value queue.
 *
 * Metadata values in the Logging library always have unique keys, but those keys can
 * have the same label. Because Log4j2 uses a String keyed map, we would risk clashing of
 * values if we just used the label to store each value directly. This class lets us store
 * a list of values for a single label while being memory efficient in the common case
 * where each label really does only have one value.
 */
internal class ValueQueue private constructor() : Iterable<Any> {

    // Since the number of elements is almost never above 1 or 2, a LinkedList saves space.
    private val values: MutableList<Any> = LinkedList()

    override fun iterator(): Iterator<Any> = values.iterator()

    fun put(item: Any?) {
        val it = checkNotNull(item, "item")
        values.add(it)
    }

    fun size(): Int = values.size

    /**
     * Returns a string representation of the contents of the specified value queue.
     * - If the value queue is empty, returns an empty string.
     * - If the value queue contains a single element `a`, returns `a.toString()`.
     * - Otherwise, the contents of the queue are formatted like a List.
     */
    @Suppress("ReturnCount")
    override fun toString(): String {
        // This case shouldn't actually happen unless you use the value queue
        // for storing emitted values.
        if (values.isEmpty()) return ""
        // Consider using MessageUtils.safeToString() here.
        if (values.size == 1) return values[0].toString()
        return values.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        other as ValueQueue
        return values == other.values
    }

    override fun hashCode(): Int = values.hashCode()

    companion object {

        @JvmStatic
        fun newQueue(item: Any): ValueQueue {
            checkNotNull(item, "item")
            val valueQueue = ValueQueue()
            valueQueue.put(item)
            return valueQueue
        }

        @JvmStatic
        fun maybeWrap(value: Any, existingValue: Any?): Any {
            return if (existingValue == null) {
                value
            } else {
                // This should only rarely happen, so a few small allocations seems acceptable.
                val existingQueue = existingValue as? ValueQueue ?: newQueue(existingValue)
                existingQueue.put(value)
                existingQueue
            }
        }

        @JvmStatic
        fun appendValues(label: String, valueOrQueue: Any, kvh: KeyValueHandler) {
            if (valueOrQueue is ValueQueue) {
                for (v in valueOrQueue) {
                    emit(label, v, kvh)
                }
            } else {
                emit(label, valueOrQueue, kvh)
            }
        }

        /**
         * Helper method for creating and initializing a value queue with a non-nullable value.
         * If value is an instance of `Tags`, each tag will be added to the value queue.
         */
        @JvmStatic
        fun appendValueToNewQueue(value: Any): ValueQueue {
            val valueQueue = ValueQueue()
            emit("", value) { _, v -> valueQueue.put(v) }
            return valueQueue
        }

        /**
         * Emits a metadata label/value pair to a given KeyValueHandler,
         * handling Tags values specially.
         *
         * Tags are key-value mappings which cannot be modified or replaced.
         * If you add the tag mapping "foo" -> true and later add "foo" -> false,
         * you get "foo" mapped to both true and false.
         * This is deliberate since the key space for tags is global and the risk
         * of two bits of code accidentally using the same tag name is real.
         *
         * Given three tag mappings:
         * - "baz" (no value)
         * - "foo" -> true
         * - "foo" -> false
         *
         * the value queue is going to store the mappings as: tags=[baz, foo=false, foo=true]
         * Reusing the label 'tags' is intentional as this allows us to store the flattened tags in
         * Log4j2's `ContextMap`.
         */
        @JvmStatic
        @Suppress("NestedBlockDepth", "ReturnCount")
        fun emit(label: String, value: Any, kvh: KeyValueHandler) {
            if (value is Tags) {
                // Flatten tags to treat them as keys or key/value pairs,
                // e.g., tags=[baz=bar, baz=bar2, foo]
                value.asMap().forEach { k, v ->
                    if (v.isEmpty()) {
                        kvh.handle(label, k)
                    } else {
                        for (obj in v) {
                            kvh.handle(label, "$k=$obj")
                        }
                    }
                }
            } else {
                kvh.handle(label, value)
            }
        }
    }
}
