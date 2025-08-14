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

package io.spine.logging

import io.spine.annotation.TestOnly

/**
 * Provides a strategy for "bucketing" a potentially unbounded set of log
 * aggregation keys used by the [LoggingApi.per] method.
 *
 * When implementing new strategies not provided by this class, it is important
 * to ensure that the [apply] method returns values from a bounded set of
 * instances wherever possible.
 *
 * This is important because the returned values are held persistently for
 * potentially many different log sites.
 *
 * If a different instance is returned each time [apply] is called, a
 * different instance will be held in each log site.
 *
 * This multiplies the amount of memory that is retained indefinitely by
 * any use of [LoggingApi.per].
 *
 * One way to handle arbitrary key types would be to create a strategy which
 * "interns" instances in some way, to produce singleton identifiers.
 *
 * Unfortunately, interning can itself be a cause of unbounded memory leaks,
 * so a bucketing strategy wishing to perform interning should probably
 * support a user defined maximum capacity to limit the overall risk.
 *
 * If too many instances are seen, the strategy should begin to return `null`
 * (and log an appropriate warning).
 *
 * The additional complexity created by this approach really tells us that
 * types which require interning in order to be used as aggregation keys
 * should be considered unsuitable, and callers should seek alternatives.
 *
 * @param name The name of this strategy, used for debugging purposes.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogPerBucketingStrategy.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public actual abstract class LogPerBucketingStrategy<T> protected constructor(
    private val name: String
) {
    /**
     * Maps a log aggregation key from a potentially unbounded set of
     * key values to a bounded set of instances.
     *
     * Implementations of this method should be efficient and avoid
     * allocating memory wherever possible.
     *
     * The returned value must be an immutable identifier with minimal additional
     * allocation requirements and ideally have singleton semantics
     * (e.g., an [Enum] or [Integer] value).
     *
     * **Warning**: If keys are not known to have natural singleton semantics
     * (e.g. [String]) then returning the given key instance is generally a bad idea.
     *
     * Even if the set of key values is small, the set of distinct allocated instances
     * passed to [LoggingApi.per] can be unbounded, and that's what matters.
     *
     * As such, it is always better to map keys to some singleton identifier or
     * intern the keys in some way.
     *
     * @param key A non-null key from a potentially unbounded set of log aggregation keys.
     * @return an immutable value from some known bounded set, which will be held persistently by
     *         internal Flogger data structures as part of the log aggregation feature. If
     *         `null` is returned, the corresponding call to `per(key, STRATEGY)` has no effect.
     */
    protected abstract fun apply(key: T): Any?

    internal fun doApply(key: T): Any? = apply(key)

    /**
     * Access to the [apply] method for testing purposes.
     *
     * This method is not part of the public API and should not be used by client code.
     */
    @TestOnly
    internal fun applyForTesting(key: T): Any? = apply(key)

    override fun toString(): String =
        "${LogPerBucketingStrategy::class.java.simpleName}[$name]"

    public companion object {

        /**
         * A strategy to use only if the set of log aggregation keys is known to be
         * a strictly bounded set of instances with singleton semantics.
         */
        private val KNOWN_BOUNDED = object : LogPerBucketingStrategy<Any>("KnownBounded") {
            override fun apply(key: Any): Any = key
        }

        /**
         * This is a "safe" strategy as far as memory use is concerned since class objects
         * are effectively singletons.
         */
        private val BY_CLASS = object : LogPerBucketingStrategy<Any>("ByClass") {
            override fun apply(key: Any): Any = key.javaClass
        }

        /**
         * This is a "safe" strategy as far as memory use is concerned, because a class object
         * returns the same string instance every time its called, and class objects
         * are effectively singletons.
         */
        private val BY_CLASS_NAME = object : LogPerBucketingStrategy<Any>("ByClassName") {
            override fun apply(key: Any): Any = key.javaClass.name /* This is a naturally interned
                value, so no need to call `intern()`. */
        }

        /**
         * A strategy to use only if the set of log aggregation keys is known to be
         * a strictly bounded set of instances with singleton semantics.
         *
         * **WARNING**: When using this strategy, keys passed to [LoggingApi.per]
         * are used as-is by the log aggregation code, and held indefinitely by internal
         * static data structures.
         *
         * As such it is vital that key instances used with this strategy have singleton semantics
         * (i.e., if `k1.equals(k2)` then `k1 == k2`).
         *
         * Failure to adhere to this requirement is likely to result in hard to detect memory leaks.
         *
         * If keys do not have singleton semantics then you should use a different strategy,
         * such as [byHashCode] or [byClass].
         */
        @JvmStatic
        public fun knownBounded(): LogPerBucketingStrategy<Any> = KNOWN_BOUNDED

        /**
         * A strategy which uses the [Class] of the given key for log aggregation.
         *
         * This is useful when you need to aggregate over specific exceptions or similar
         * type-distinguished instances.
         *
         * Note that using this strategy will result in a reference to the [Class] object of
         * the key being retained indefinitely.
         *
         * This will prevent class unloading from occurring for affected classes, and
         * it is up to the caller to decide if this is acceptable or not.
         */
        @JvmStatic
        public fun byClass(): LogPerBucketingStrategy<Any> = BY_CLASS

        /**
         * A strategy which uses the [Class] name of the given key for log aggregation.
         *
         * This is useful when you need to aggregate over specific exceptions or similar
         * type-distinguished instances.
         *
         * This is an alternative strategy to [byClass] which avoids holding onto the class
         * instance and avoids any issues with class unloading.
         *
         * However, it may conflate classes if applications use complex arrangements of custom
         * class-loaders, but this should be extremely rare.
         */
        @JvmStatic
        public fun byClassName(): LogPerBucketingStrategy<Any> = BY_CLASS_NAME

        /**
         * A strategy defined for some given set of known keys.
         *
         * Unlike [knownBounded], this strategy maps keys to a bounded set of identifiers, and
         * permits the use of non-singleton keys in [LoggingApi.per].
         *
         * If keys outside this set are used this strategy returns `null`, and
         * log aggregation will not occur.
         *
         * Duplicates in [knownKeys] are ignored.
         */
        @JvmStatic
        public fun forKnownKeys(knownKeys: Iterable<Any>): LogPerBucketingStrategy<Any> {
            val keyMap = HashMap<Any, Int>()
            val name = buildString {
                append("ForKnownKeys(")
                var index = 0
                for (key in knownKeys) {
                    if (!keyMap.containsKey(key)) {
                        if (index > 0) {
                            append(", ")
                        }
                        append(key)
                        keyMap[key] = index
                        index++
                    }
                }
                append(')')
            }
            // We check here to avoid querying `knownKeys` size just for the precondition check.
            require(!keyMap.isEmpty()) { "`knownKeys` must not be empty." }
            return object : LogPerBucketingStrategy<Any>(name) {
                override fun apply(key: Any): Any? = keyMap[key]
            }
        }

        /**
         * A strategy which uses the [hashCode] of a given key, modulo [maxBuckets], for
         * log aggregation.
         *
         * This is a fallback strategy for cases where the set of possible values is
         * not known in advance or could be arbitrarily large in unusual circumstances.
         *
         * When using this method, it is obviously important that the [hashCode] method of
         * the expected keys is well distributed, since duplicate hash codes, or hash codes
         * congruent to [maxBuckets] will cause keys to be conflated.
         *
         * The caller is responsible for deciding the number of unique log aggregation keys this
         * strategy can return. This choice is a trade-off between memory usage and the risk of
         * conflating keys when performing log aggregation.
         *
         * Each log site using this strategy will hold up to [maxBuckets] distinct versions of log
         * site information to allow rate limiting and other stateful operations to be applied
         * separately per bucket.
         *
         * The overall allocation cost depends on the type of rate limiting used alongside this
         * method, but it scales linearly with [maxBuckets].
         *
         * It is recommended to keep the value of [maxBuckets] below 250, since this
         * guarantees no additional allocations will occur when using this strategy.
         * However, the value chosen should be as small as practically possible for
         * the typical expected number of unique keys.
         *
         * To avoid unwanted allocation at log sites, users are strongly encouraged to assign the
         * returned value to a static field and pass that to any log statements which need it.
         */
        @JvmStatic
        public fun byHashCode(maxBuckets: Int): LogPerBucketingStrategy<Any> {
            require(maxBuckets > 0) { "`maxBuckets` must be positive: $maxBuckets." }
            return object : LogPerBucketingStrategy<Any>("ByHashCode($maxBuckets)") {
                @Suppress("MagicNumber")
                override fun apply(key: Any): Any =
                    Math.floorMod(key.hashCode(), maxBuckets) - 128
            }
        }
    }
}
