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

package io.spine.logging.jvm.backend

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.util.Checks.checkArgument
import io.spine.logging.jvm.util.Checks.checkNotNull
import java.util.HashMap

/**
 * Callback API for logger backend implementations to handle metadata keys/values.
 *
 * The API methods will be called once for each distinct key, in encounter order.
 * Different methods are called depending on whether the key is repeatable or not.
 *
 * It is expected that the most convenient way to construct a metadata handler is
 * via the [Builder] class, which lets keys be individually mapped to callbacks.
 * However, the class can also just be extended to implement alternate/custom behavior.
 *
 * @param C The arbitrary context type.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MetadataHandler.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class MetadataHandler<C : Any> {

    /**
     * Handles a single metadata key/value mapping.
     *
     * This method is called directly for singleton (non-repeatable) keys, but may also
     * be called for repeated keys by the default implementation of [handleRepeated].
     * It is up to the implementation to override that method if this behaviour is unwanted.
     *
     * @param key The metadata key (not necessarily a "singleton" key).
     * @param value The associated metadata value.
     * @param context An arbitrary context object supplied to the process method.
     * @param T The key/value type.
     */
    protected abstract fun <T : Any> handle(key: MetadataKey<T>, value: T, context: C)

    /**
     * Handles values for a repeatable metadata key. The method is called for
     * all repeatable keys (even those with only one value).
     *
     * The default implementation makes repeated callbacks to the [handle] method,
     * in order, for each value.
     *
     * @param key The repeatable metadata key.
     * @param values A lightweight iterator over all values associated with the key.
     *        Note that this instance is read-only and must not be held beyond
     *        the scope of this callback.
     * @param context An arbitrary context object supplied to the process method.
     * @param T The key/value type.
     */
    protected open fun <T : Any> handleRepeated(
        key: MetadataKey<T>,
        values: Iterator<T>,
        context: C
    ) {
        while (values.hasNext()) {
            handle(key, values.next(), context)
        }
    }

    /**
     * API for handling metadata key/value pairs individually.
     *
     * @param T The key/value type.
     * @param C The type of the context passed to the callbacks.
     */
    public fun interface ValueHandler<T : Any, C> {

        /**
         * Handles metadata values individually.
         *
         * @param key The metadata key (not necessarily a "singleton" key).
         * @param value The associated metadata value.
         * @param context An arbitrary context object supplied to the process method.
         */
        public fun handle(key: MetadataKey<T>, value: T, context: C)
    }

    /**
     * API for handling repeated metadata key/values in a single callback.
     *
     * @param T The key/value type.
     * @param C The type of the context passed to the callbacks.
     */
    public fun interface RepeatedValueHandler<T : Any, C> {
        /**
         * Handles all repeated metadata values for a given key.
         *
         * @param key The repeatable metadata key for which this handler was registered,
         *        or an unknown key if this is the default handler.
         * @param values A lightweight iterator over all values associated with the key.
         *        Note that this instance is read-only and must not be held beyond
         *        the scope of this callback.
         * @param context An arbitrary context object supplied to the process method.
         */
        public fun handle(key: MetadataKey<T>, values: Iterator<T>, context: C)
    }

    /**
     * Builder for a map-based [MetadataHandler] which allows handlers to
     * be associated with individual callbacks.
     *
     * @param C The context type.
     */
    public class Builder<C : Any> internal constructor(
        internal val defaultHandler: ValueHandler<Any, in C>
    ) {
        internal val singleValueHandlers = HashMap<MetadataKey<*>, ValueHandler<*, in C>>()
        internal val repeatedValueHandlers =
            HashMap<MetadataKey<*>, RepeatedValueHandler<*, in C>>()
        internal var defaultRepeatedHandler: RepeatedValueHandler<Any, in C>? = null

        /**
         * Sets a handler for any unknown repeated keys, which allows values to be processed via a
         * generic [Iterator]. To handle repeated values against a known key with their expected
         * type, register a handler via [addRepeatedHandler].
         *
         * Note that if a repeated key is associated with an individual value handler (i.e., via
         * [addHandler]), then that will be used in preference to the default handler set here.
         *
         * @param defaultHandler The default handler for unknown repeated keys/values.
         * @return The builder instance for chaining.
         */
        @CanIgnoreReturnValue
        public fun setDefaultRepeatedHandler(
            defaultHandler: RepeatedValueHandler<Any, in C>
        ): Builder<C> {
            this.defaultRepeatedHandler = checkNotNull(defaultHandler, "handler")
            return this
        }

        /**
         * Registers a value handler for the specified key, replacing any
         * previously registered value.
         *
         * @param key The key for which the handler should be invoked (can be a repeated key).
         * @param handler The value handler to be invoked for every value associated with the key.
         * @param T The key/value type.
         * @return The builder instance for chaining.
         */
        @CanIgnoreReturnValue
        public fun <T : Any> addHandler(
            key: MetadataKey<T>,
            handler: ValueHandler<in T, in C>
        ): Builder<C> {
            checkNotNull(key, "key")
            checkNotNull(handler, "handler")
            repeatedValueHandlers.remove(key)
            @Suppress("UNCHECKED_CAST")
            singleValueHandlers[key] = handler as ValueHandler<*, in C>
            return this
        }

        /**
         * Registers a repeated value handler for the specified key, replacing any previously
         * registered value.
         *
         * @param key The repeated key for which the handler should be invoked.
         * @param handler The repeated value handler to be invoked once for all associated values.
         * @param T The key/value type.
         * @return The builder instance for chaining.
         */
        @CanIgnoreReturnValue
        public fun <T : Any> addRepeatedHandler(
            key: MetadataKey<out T>,
            handler: RepeatedValueHandler<T, in C>
        ): Builder<C> {
            checkNotNull(key, "key")
            checkNotNull(handler, "handler")
            checkArgument(key.canRepeat(), "key must be repeating")
            singleValueHandlers.remove(key)
            @Suppress("UNCHECKED_CAST")
            repeatedValueHandlers[key] = handler as RepeatedValueHandler<*, in C>
            return this
        }

        /**
         * Registers "no op" handlers for the given keys, resulting in their values being ignored.
         *
         * @param key A key to ignore in the builder.
         * @param rest Additional keys to ignore in the builder.
         * @return The builder instance for chaining.
         */
        @CanIgnoreReturnValue
        public fun ignoring(key: MetadataKey<*>, vararg rest: MetadataKey<*>): Builder<C> {
            checkAndIgnore(key)
            for (k in rest) {
                checkAndIgnore(k)
            }
            return this
        }

        /**
         * Registers "no op" handlers for the given keys, resulting in their values being ignored.
         *
         * @param keys The keys to ignore in the builder.
         * @return The builder instance for chaining.
         */
        @CanIgnoreReturnValue
        public fun ignoring(keys: Iterable<MetadataKey<*>>): Builder<C> {
            for (k in keys) {
                checkAndIgnore(k)
            }
            return this
        }

        private fun <T : Any> checkAndIgnore(key: MetadataKey<T>) {
            checkNotNull(key, "key")
            // It is more efficient to ignore a repeated key explicitly.
            if (key.canRepeat()) {
                @Suppress("UNCHECKED_CAST")
                addRepeatedHandler(key, IGNORE_REPEATED_VALUE as RepeatedValueHandler<T, in C>)
            } else {
                @Suppress("UNCHECKED_CAST")
                addHandler(key, IGNORE_VALUE as ValueHandler<T, in C>)
            }
        }

        /**
         * Removes any existing handlers for the given keys,
         * returning them to the default handler(s).
         *
         * This method is useful when making several handlers with
         * different mappings from a single builder.
         *
         * @param key A key to remove from the builder.
         * @param rest Additional keys to remove from the builder.
         * @return The builder instance for chaining.
         */
        @CanIgnoreReturnValue
        public fun removeHandlers(key: MetadataKey<*>, vararg rest: MetadataKey<*>): Builder<C> {
            checkAndRemove(key)
            for (k in rest) {
                checkAndRemove(k)
            }
            return this
        }

        private fun checkAndRemove(key: MetadataKey<*>) {
            checkNotNull(key, "key")
            singleValueHandlers.remove(key)
            repeatedValueHandlers.remove(key)
        }

        /** Returns the immutable, map-based metadata handler. */
        public fun build(): MetadataHandler<C> {
            return MapBasedHandler(this)
        }

        private companion object {
            // Since the context is ignored, this can safely be cast to `ValueHandler<Any, C>`.
            private val IGNORE_VALUE = ValueHandler<Any, Any> { _, _, _ -> /* No op. */ }

            // Since the context is ignored, this can safely
            // be cast to RepeatedValueHandler<Any, C>.
            private val IGNORE_REPEATED_VALUE =
                RepeatedValueHandler<Any, Any> { _, _, _ -> /* No op. */ }
        }
    }

    public companion object {

        /**
         * Returns a builder for a handler with the specified default callback.
         *
         * The default handler will receive all key/value pairs from the metadata individually,
         * which can result in repeated keys being seen more than once.
         *
         * A default handler is required because no handler can know the complete set of
         * keys which might be available and it is very undesirable to drop unknown keys.
         *
         * If default repeated values should be handled together,
         * [Builder.setDefaultRepeatedHandler] should be called as well.
         *
         * Unknown keys/values can only be handled in a generic fashion unless
         * a given key is matched to a known constant.
         * However, the entire point of this map-based handler is to avoid any need to
         * do explicit matching, so the default handler should not need to know the value type.
         *
         * @param defaultHandler The default handler for unknown keys/values.
         * @param C The context type.
         */
        @JvmStatic
        public fun <C : Any> builder(defaultHandler: ValueHandler<Any, in C>): Builder<C> =
            Builder(defaultHandler)
    }
}

private class MapBasedHandler<C : Any>(builder: Builder<C>) : MetadataHandler<C>() {
    private val singleValueHandlers = HashMap<MetadataKey<*>, ValueHandler<*, in C>>()
    private val repeatedValueHandlers = HashMap<MetadataKey<*>, RepeatedValueHandler<*, in C>>()
    private val defaultHandler: ValueHandler<Any, in C>
    private val defaultRepeatedHandler: RepeatedValueHandler<Any, in C>?

    init {
        this.singleValueHandlers.putAll(builder.singleValueHandlers)
        this.repeatedValueHandlers.putAll(builder.repeatedValueHandlers)
        this.defaultHandler = builder.defaultHandler
        this.defaultRepeatedHandler = builder.defaultRepeatedHandler
    }

    @Suppress("UNCHECKED_CAST") // See comments for why casting is safe.
    override fun <T : Any> handle(key: MetadataKey<T>, value: T, context: C) {
        // Safe cast because of how our private map is managed.
        val handler = singleValueHandlers[key] as ValueHandler<T, in C>?
        if (handler != null) {
            handler.handle(key, value, context)
        } else {
            defaultHandler.handle(
                // Casting MetadataKey<T> to "<? super T>" is safe since it
                // only produces elements of 'T'.
                key as MetadataKey<Any>,
                value,
                context
            )
        }
    }

    @Suppress("UNCHECKED_CAST") // See comments for why casting is safe.
    override fun <T : Any> handleRepeated(
        key: MetadataKey<T>,
        values: Iterator<T>,
        context: C
    ) {
        // Safe cast because of how our private map is managed.
        val handler = repeatedValueHandlers[key] as RepeatedValueHandler<T, in C>?
        if (handler != null) {
            handler.handle(key, values, context)
        } else if (defaultRepeatedHandler != null && !singleValueHandlers.containsKey(key)) {
            defaultRepeatedHandler.handle(

                // Casting MetadataKey<T> to "<? super T>" is safe since it
                // only produces elements of 'T'.
                key as MetadataKey<Any>,

                // Casting the iterator is safe since it also only produces elements of 'T'.
                values as Iterator<Any>,

                context
            )
        } else {
            // Dispatches keys individually.
            super.handleRepeated(key, values, context)
        }
    }
}
