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

package io.spine.logging.jvm;

import io.spine.logging.jvm.backend.Metadata;

import java.util.concurrent.ConcurrentHashMap;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * Provides per log site state for stateful fluent logging operations (e.g. rate limiting).
 *
 * <p>A log site map allows a logging API to efficiently, and safely, retrieve mutable log site
 * state. This state can then be updated according to the current log statement.
 *
 * <p>Note that values held in this map are expected to be mutable and must still be thread safe
 * themselves (the map protects only from concurrent lookup, not concurrent modification of the
 * state itself). It is also strongly advised that all implementations of log site state avoid using
 * locking (e.g. "synchronized" data structres) due to the risk of causing not trivial and
 * potentially harmful thread contention bottlenecks during logging.
 *
 * <p>This class is intended only for use by fluent logging APIs (subclasses of {@link LogContext}
 * and only used in the {@link LogContext#postProcess(LogSiteKey)} method, which supplies the key
 * appropriate for the current log statement.
 *
 * @param <V>
 *         The value type in the map.
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogSiteMap.java">
 *         Original Java code of Google Flogger</a>
 */
public abstract class LogSiteMap<V> {

    private final ConcurrentHashMap<LogSiteKey, V> concurrentMap = new ConcurrentHashMap<>();

    protected LogSiteMap() {
    }

    /**
     * Implemented by subclasses to provide a new value for a newly added keys. This value is mapped
     * to the key and cannot be replaced, so it is expected to be mutable and must be thread safe.
     * All values in a {@code LogSiteMap} are expected to be the same type and have the same initial
     * state.
     */
    protected abstract V initialValue();

    // This method exists only for testing. Do not make this public.
    boolean contains(LogSiteKey key) {
        return concurrentMap.containsKey(key);
    }

    /**
     * Returns the mutable, thread safe, log site state for the given key to be read or updated
     * during
     * the {@link LogContext#postProcess(LogSiteKey)} method.
     *
     * <p>Note that due to the possibility of log site key specialization, there may be more than
     * one
     * value in the map for any given log site. This is intended and allows for things like per
     * scope
     * rate limiting.
     */
    public final V get(LogSiteKey key, Metadata metadata) {
        V value = concurrentMap.get(key);
        if (value != null) {
            return value;
        }
        // Many threads can get here concurrently and attempt to add an initial value.
        value = checkNotNull(initialValue(), "initial map value");
        V race = concurrentMap.putIfAbsent(key, value);
        if (race != null) {
            return race;
        }
        // Only one thread gets here for each log site key added to this map.
        addRemovalHook(key, metadata);
        return value;
    }

    private void addRemovalHook(final LogSiteKey key, Metadata metadata) {
        Runnable removalHook = null;
        for (int i = 0, count = metadata.size(); i < count; i++) {
            if (!LogContext.Key.LOG_SITE_GROUPING_KEY.equals(metadata.getKey(i))) {
                continue;
            }
            Object groupByKey = metadata.getValue(i);
            if (!(groupByKey instanceof LoggingScope)) {
                continue;
            }
            if (removalHook == null) {
                // Non-static inner class references the outer LogSiteMap.
                removalHook = new Runnable() {
                    @Override
                    public void run() {
                        concurrentMap.remove(key);
                    }
                };
            }
            ((LoggingScope) groupByKey).onClose(removalHook);
        }
    }
}
