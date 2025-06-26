/*
 * Copyright 2021, The Flogger Authors; 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.logging.backend.log4j2;

import io.spine.logging.jvm.MetadataKey.KeyValueHandler;
import io.spine.logging.jvm.context.Tags;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * A simple FIFO queue linked-list implementation designed to store multiple
 * metadata values in a {@link org.apache.logging.log4j.util.StringMap StringMap}.
 *
 * <p>There are two aspects worth pointing out:
 *
 * <p>First, it is expected that a value queue always contains at least
 * a single item. You cannot add null references to the queue, and you cannot
 * create an empty queue.
 *
 * <p>Second, it is expected to access the contents of the value queue via
 * an iterator only. Hence, we do not provide a method for taking the first
 * item in the value queue.
 *
 * <p>Metadata values in Flogger always have unique keys, but those keys can
 * have the same label. Because Log4j2 uses a {@code String} keyed map,
 * we would risk clashing of values if we just used the label to store each
 * value directly. This class lets us store a list of values for a single
 * label while being memory efficient in the common case where each label
 * really does only have one value.
 */
@SuppressWarnings("UnnecessarilyQualifiedStaticUsage")
final class ValueQueue implements Iterable<Object> {

    // Since the number of elements is almost never above 1 or 2, a LinkedList saves space.
    private final List<Object> values = new LinkedList<>();

    private ValueQueue() {
    }

    static ValueQueue newQueue(Object item) {
        checkNotNull(item, "item");
        var valueQueue = new ValueQueue();
        valueQueue.put(item);
        return valueQueue;
    }

    static Object maybeWrap(Object value, @Nullable Object existingValue) {
        checkNotNull(value, "value");
        if (existingValue == null) {
            return value;
        } else {
            // This should only rarely happen, so a few small allocations seems acceptable.
            var existingQueue =
                    existingValue instanceof ValueQueue
                    ? (ValueQueue) existingValue
                    : ValueQueue.newQueue(existingValue);
            existingQueue.put(value);
            return existingQueue;
        }
    }

    static void appendValues(String label, Object valueOrQueue, KeyValueHandler kvh) {
        if (valueOrQueue instanceof ValueQueue) {
            for (var value : (ValueQueue) valueOrQueue) {
                emit(label, value, kvh);
            }
        } else {
            emit(label, valueOrQueue, kvh);
        }
    }

    /**
     * Helper method for creating and initializing a value queue with a non-nullable value. If value
     * is an instance of Tags, each tag will be added to the value queue.
     */
    static ValueQueue appendValueToNewQueue(Object value) {
        var valueQueue = new ValueQueue();
        ValueQueue.emit(null, value, (k, v) -> valueQueue.put(v));
        return valueQueue;
    }

    /**
     * Emits a metadata label/value pair to a given {@code KeyValueHandler}, handling {@code Tags}
     * values specially.
     *
     * <p>Tags are key-value mappings which cannot be modified or replaced. If you add the tag
     * mapping
     * {@code "foo" -> true} and later add {@code "foo" -> false}, you get "foo" mapped to both
     * true
     * and false. This is very deliberate since the key space for tags is global and the risk of
     * two
     * bits of code accidentally using the same tag name is real (e.g. you add "id=xyz" to a scope,
     * but you see "id=abcd" because someone else added "id=abcd" in a context you weren't aware
     * of).
     *
     * <p>Given three tag mappings:
     * <ul>
     *   <li>{@code "baz"} (no value)
     *   <li>{@code "foo" -> true}
     *   <li>{@code "foo" -> false}
     * </ul>
     *
     * the value queue is going to store the mappings as:
     * <pre>{@code
     * tags=[baz, foo=false, foo=true]
     * }</pre>
     *
     * <p>Reusing the label 'tags' is intentional as this allows us to store the flatten tags in
     * Log4j2's ContextMap.
     */
    static void emit(String label, Object value, KeyValueHandler kvh) {
        if (value instanceof Tags) {
            // Flatten tags to treat them as keys or key/value pairs,
            // e.g., tags=[baz=bar, baz=bar2, foo]
            ((Tags) value)
                    .asMap()
                    .forEach(
                            (k, v) -> {
                                if (v.isEmpty()) {
                                    kvh.handle(label, k);
                                } else {
                                    for (var obj : v) {
                                        kvh.handle(label, k + '=' + obj);
                                    }
                                }
                            });
        } else {
            kvh.handle(label, value);
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return values.iterator();
    }

    void put(Object item) {
        checkNotNull(item, "item");
        values.add(item);
    }

    int size() {
        return values.size();
    }

    /**
     * Returns a string representation of the contents of the specified value queue.
     *
     * <ul>
     *   <li>If the value queue is empty, the method returns an empty string.
     *   <li>If the value queue contains a single element {@code a}, this method returns {@code
     *       a.toString()}.
     *   <li>Otherwise, the contents of the queue are formatted like a {@code List}.
     * </ul>
     */
    @Override
    public String toString() {
        // This case shouldn't actually happen unless you use the value queue
        // for storing emitted values.
        if (values.isEmpty()) {
            return "";
        }
        // Consider using MessageUtils.safeToString() here.
        if (values.size() == 1) {
            return values.get(0)
                         .toString();
        }
        return values.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (ValueQueue) o;
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }
}
