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

package io.spine.logging.jvm

import io.spine.logging.jvm.backend.Platform
import io.spine.logging.jvm.util.Checks
import org.jetbrains.annotations.VisibleForTesting

/**
 * A key for logging semi-structured metadata values.
 *
 * Metadata keys can be used to provide log statements with strongly typed values which can be
 * read and interpreted by logging backends or other logs related tools. This mechanism is
 * intended for values with specific semantics and should not be seen as a replacement for
 * logging arguments as part of a formatted log message.
 *
 * Examples of where using `MetadataKey` is suitable are:
 *
 * * Logging a value with special semantics (e.g., values that are handled specially by the
 *   logger backend).
 * * Passing configuration to a specific logger backend to modify behaviour for individual log
 *   statements or all log statements in a `ScopedLoggingContext`.
 * * Logging a structured value in many places with consistent formatting (e.g., so it can later
 *   be re-parsed by logs related tools).
 *
 * If you just want to log a general "key value pair" in a small number of log statements, it is
 * still better to just do something like `log("key=%s", value)`.
 *
 * Metadata keys are expected to be singleton constants, and should never be allocated at the log
 * site itself. Even though they are expected to be singletons, comparing keys should be done via
 * `equals()` (rather than '==') since this will be safe in cases where non-singleton keys
 * exist, and is just as fast if the keys are singletons.
 *
 * It is strongly recommended that any public [MetadataKey] instances are defined as
 * `public static final` fields in a top-level or nested class, which does no logging.
 * Ideally a separate class would be defined to hold only the keys, since this allows keys
 * to be loaded very early in the logging [Platform] lifecycle without risking any static
 * initialization issues.
 *
 * Custom subclasses of `MetadataKey` which override either of the protected [.emit] methods
 * should take care to avoid calling any code, which might trigger logging since this could lead
 * to unexpected recursion, especially if the key is being logged as part of a
 * `ScopedLoggingContext`. While there is protection against unbounded reentrant logging in
 * Flogger, it is still best practice to avoid it where possible.
 *
 * Metadata keys are passed to a log statement via the `with()` method, so it can aid
 * readability to choose a name for the constant field which reads "fluently" as part of the log
 * statement. For example:
 *
 * ```
 * // Prefer this...
 * logger.atInfo().with(FILE_LOGGING_FOR, user).log("User specific log message...");
 * // to...
 * logger.atInfo().with(SET_LOGGING_TO_USER_FILE, user).log("User specific log message...");
 * ```
 *
 * Logger backends can act upon metadata present in log statements to modify behavior.
 * Any metadata entries that are not handled by a backend explicitly are, by default,
 * rendered as part of the log statement in a default format.
 *
 * Note that some metadata entries are handled before being processed by the backend
 * (e.g., rate limiting), but a metadata entry remains present to record that rate limiting
 * was enabled.
 *
 * @param T The type of values associated with this key.
 * @property label A short human-readable text label.
 * @property clazz The class representing the type [T].
 * @property canRepeat Whether this key supports multiple values.
 * @property isCustom Whether this is a custom key with overridden emit methods.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/MetadataKey.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public open class MetadataKey<T : Any> private constructor(
    label: String,
    private val clazz: Class<out T>,
    override val canRepeat: Boolean,
    private val isCustom: Boolean
) : io.spine.logging.MetadataKey<T> {

    /**
     * Callback interface to handle additional contextual `Metadata` in log statements. This
     * interface is only intended to be implemented by logger backend classes as part of handling
     * metadata, and should not be used in any general application code, other than to implement the
     * [MetadataKey.emit] method in this class.
     */
    public fun interface KeyValueHandler {

        /**
         * Handle a single key/value a pair of contextual metadata for a log statement.
         */
        public fun handle(key: String, value: Any?)
    }

    /**
     * A short, human-readable text label which will prefix the metadata in cases
     * where it is formatted as part of the log message.
     */
    override val label: String = Checks.checkMetadataIdentifier(label)

    /**
     * A 64-bit bloom filter mask for this metadata key, usable by backend implementations
     * to efficiently determine the uniqueness of keys (e.g., for deduplication and grouping).
     *
     * This value is calculated on the assumption that there are normally not more than 10 distinct
     * metadata keys being processed at any time. If more distinct keys need to be processed using
     * this Bloom Filter mask, it will result in a higher than optimal false-positive rate.
     */
    public val bloomFilterMask: Long = createBloomFilterMaskFromSystemHashcode(this)

    /**
     * Constructor for custom key subclasses.
     *
     * Most use-cases will not require the use of custom keys, but occasionally it can be useful
     * to create a specific subtype to control the formatting of values or to have a family of
     * related keys with a common parent type.
     *
     * @param label A short human-readable text label.
     * @param clazz The class representing the type [T].
     * @param canRepeat Whether this key supports multiple values.
     */
    protected constructor(label: String, clazz: Class<out T>, canRepeat: Boolean) :
            this(label, clazz, canRepeat, true)

    /**
     * Cast an arbitrary value to the type of this key.
     */
    override fun cast(value: Any?): T? = clazz.cast(value)

    /**
     * Whether this key can be used to set more than one value in the metadata.
     */
    public fun canRepeat(): Boolean = canRepeat

    /**
     * Emits one or more key/value pairs for the given metadata value. Call this method in
     * preference to using [.emitRepeated] directly to protect against unbounded reentrant
     * logging.
     */
    public fun safeEmit(value: T, kvh: KeyValueHandler) {
        if (isCustom &&
            Platform.getCurrentRecursionDepth() > MAX_CUSTOM_META_DATAKEY_RECURSION_DEPTH
        ) {
            // Recursive logging detected, possibly caused by custom metadata
            // keys triggering reentrant logging.
            // To halt recursion, emit the keys in the default non-custom format
            // without invoking user overridable methods.
            kvh.handle(this.label, value)
        } else {
            emit(value, kvh)
        }
    }

    /**
     * Emits one or more key/value pairs for a sequence of repeated metadata values.
     *
     * Call this method in preference to using [emitRepeated] directly to protect
     * against unbounded reentrant logging.
     */
    public fun safeEmitRepeated(values: Iterator<T>, kvh: KeyValueHandler) {
        check(canRepeat) { "non repeating key" }
        if (isCustom &&
            Platform.getCurrentRecursionDepth() > MAX_CUSTOM_META_DATAKEY_RECURSION_DEPTH
        ) {
            // Recursive logging detected, possibly caused by custom metadata
            // keys triggering reentrant logging.
            // To halt recursion, emit the keys in the default non-custom format
            // without invoking user overridable methods.
            while (values.hasNext()) {
                kvh.handle(this.label, values.next())
            }
        } else {
            emitRepeated(values, kvh)
        }
    }

    /**
     * Override this method to provide custom logic for emitting one or more key/value pairs for a
     * given metadata value (call [.safeEmit] from logging code to actually emit values).
     *
     * By default, this method simply emits the given value with this key's label, but it can be
     * customized key/value pairs if necessary.
     *
     * Note that if multiple key/value pairs are emitted, the following best-practice
     * should be followed:
     *
     * * Key names should be of the form `"<label>.<suffix>"`.
     * * Suffixes should only contain lower case ASCII letters and underscore (i.e., `[a-z_]`).
     *
     * This method is called as part of logs processing and could be invoked a very large number of
     * times in performance-critical code. Implementations must be very careful to avoid calling any
     * code, which might risk deadlocks, stack overflow, concurrency issues or performance problems.
     * In particular, implementations of this method should be careful to avoid:
     *
     * * Calling any code, which could log using the same `MetadataKey` instance (unless you
     *   implement protection against reentrant calling in this method).
     * * Calling code which might block (e.g., performing file I/O or acquiring locks).
     * * * Allocating non-trivial amounts of memory (e.g., recording values in an unbounded data
     *   structure).
     *
     * If you do implement a `MetadataKey` with non-trivial value processing,
     * you should always make it very clear in the documentation that the key may
     * not be suitable for widespread use.
     *
     * By default, this method just calls `out.handle(getLabel(), value)`.
     */
    protected open fun emit(value: T, kvh: KeyValueHandler): Unit =
        kvh.handle(this.label, value)

    /**
     * Override this method to provide custom logic for emitting one or more
     * key/value pairs for a sequence of metadata values.
     *
     * Call [safeEmitRepeated] from logging code to actually emit values.
     *
     * Emits one or more key/value pairs for a sequence of repeated metadata values.
     * By default, this method simply calls [.emit] once for each value, in order.
     * However, it could be overridden to treat the sequence of values for a repeated key
     * as a single entity (e.g., by joining elements with a separator).
     *
     * See the [emit] method for additional caveats for custom implementations.
     */
    protected open fun emitRepeated(values: Iterator<T>, kvh: KeyValueHandler) {
        while (values.hasNext()) {
            emit(values.next(), kvh)
        }
    }

    /**
     * Opens the `protected` function [emitRepeated] for tests.
     */
    @VisibleForTesting
    internal fun emitRepeatedForTests(values: Iterator<T>, kvh: KeyValueHandler) =
        emitRepeated(values, kvh)

    /**
     * Prevent subclasses using `toString()` for anything unexpected.
     */
    override fun toString(): String =
        javaClass.getName() + '/' + label + '[' + clazz.getName() + ']'

    public companion object {

        /**
         * Creates a new instance with the given label, class, and `canRepeat` flag.
         *
         * This factory function opens the access to the `protected`
         * constructor of [MetadataKey] with the same parameters.
         */
        @VisibleForTesting
        internal fun <T : Any> of(
            label: String,
            clazz: Class<out T>,
            canRepeat: Boolean
        ): MetadataKey<T> = MetadataKey(label, clazz, canRepeat)

        /**
         * The limit for the depth of collecting metadata.
         *
         * High levels of reentrant logging could well be caused by custom metadata keys.
         * This is set lower than the total limit on reentrant logging because it is one
         * of the more likely ways in which unbounded reentrant logging could occur,
         * but it is also easy to mitigate.
         */
        private const val MAX_CUSTOM_META_DATAKEY_RECURSION_DEPTH = 20

        /**
         * Creates a key for a single piece of metadata. If metadata is set more than once using this
         * key for the same log statement, the last set value will be the one used, and other values
         * will be ignored (although callers should never rely on this behavior).
         *
         * Key instances behave like singletons, and two key instances with the same label will
         * still be considered distinct. The recommended approach is to always assign `MetadataKey`
         * instances to static final constants.
         *
         * When calling from Kotlin, please give preference to `MetadataKeysKt.singleKey()`.
         * In Kotlin, there's no explicit difference between primitive and object classes.
         *
         * When compiling to JVM, it is resolved during the compilation. An accident passing of a
         * potentially primitive class may lead to a runtime exception because metadata keys
         * are used with generics.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> single(label: String, clazz: Class<out T>): MetadataKey<T> =
            MetadataKey(label, clazz, canRepeat = false, isCustom = false)

        /**
         * Creates a single instance of [MetadataKey] with the given [label].
         *
         * @param T The type
         * @see [single]
         */
        public inline fun <reified T : Any> single(label: String): MetadataKey<T> =
            single(label, T::class.java)

        /**
         * Creates a key for a repeated piece of metadata.
         *
         * If metadata is added more than once using this key for a log statement,
         * all values will be retained as key/value pairs in the order they were added.
         *
         * Key instances behave like singletons, and two key instances with the same label will
         * still be considered distinct. The recommended approach is to always assign `MetadataKey`
         * instances to static final constants.
         *
         * When calling from Kotlin, give preference to `MetadataKeysKt.repeatedKey()`.
         *
         * In Kotlin, there is no explicit difference between primitive and object classes.
         *
         * When compiling to JVM, it is resolved during the compilation.
         * An accident passing of a potentially primitive class may lead to a runtime
         * exception because metadata keys are used with generics.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> repeated(label: String, clazz: Class<out T>): MetadataKey<T> =
            MetadataKey(label, clazz, canRepeat = true, isCustom = false)

        /**
         * The Kotlin version of []
         */
        public inline fun <reified T : Any> repeated(label: String): MetadataKey<T> =
            repeated(label, T::class.java)
    }
}

/**
 * Creates a [Bloom filter][https://en.wikipedia.org/wiki/Bloom_filter] mask
 * for the hashcode of the given [instance].
 *
 * From https://en.wikipedia.org/wiki/Bloom_filter the number of hash bits to minimize false
 * positives is:
 *   k = (M / N) ln(2)
 * where:
 *   k = number of "hash functions" which in our case is the number of bits in the filter mask.
 *   M = number of bits available (in our case 64)
 *   N = number of elements in the array (variable but almost always < 10).
 *
 * This gives a bit count of ~5 bits per mask, which is convenient since that's easily available
 * by just masking out successive 6-bit chunks in a 32-bit hashcode.
 */
@Suppress("MagicNumber")
private fun createBloomFilterMaskFromSystemHashcode(instance: Any): Long {
    // In tests (JDK11) the identity hashcode on its own was as good, if not better than, applying
    // a "mix" operation such as found in:
    // https://github.com/google/guava/blob/master/guava/src/com/google/common/hash/Murmur3_32HashFunction.java#L234
    var hash = System.identityHashCode(instance)
    var bloom = 0L
    // Bottom 6-bits form a value from 0-63 (the bit index in the Bloom Filter), and we can extract
    // 5 of these for a 32-bit value (see above for why 5 bits per mask is enough).
    repeat(5) {
        bloom = bloom or (1L shl (hash and 0x3F))
        hash = hash ushr 6
    }
    return bloom
}

/**
 * Throws [IllegalArgumentException] if the given [key] can be
 * used to set more than one value in the metadata.
 */
internal fun checkCannotRepeat(key: MetadataKey<*>) {
    require(!key.canRepeat()) { "The key must be single-valued: `$key`." }
}
