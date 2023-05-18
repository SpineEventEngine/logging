/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging

import kotlin.reflect.KClass

/**
 * Converts values of type [A] to ones of type [B] and [backward][reverse].
 */
public open class Converter<A: Any, B: Any>(
    private val forward: (A) -> B,
    private val backward: (B) -> A
) {
    public val reverse: Converter<B, A> by lazy {
        Converter(backward, forward)
    }

    /**
     * Invokes [forward] conversion.
     */
    public operator fun invoke(a: A): B = forward(a)

    override fun equals(other: Any?): Boolean {
        return if (other is Converter<*, *>) {
            forward == other.forward && backward == other.backward
        } else {
            false
        }
    }

    override fun hashCode(): Int = forward.hashCode()
}

/**
 * Converts logging levels to an external logging level type of type [T], and backward.
 *
 * A level converter must be [registered][register] before invoking the [conversion][convert].
 */
public abstract class LevelConverter<T : Any>(
    forward: (Level) -> T,
    backward: (T) -> Level
) : Converter<Level, T>(forward, backward) {

    public companion object {

        private val registry: MutableMap<KClass<*>, LevelConverter<*>> = mutableMapOf()

        /**
         * Obtains a converter for levels of the type [T].
         */
        public fun <T: Any> get(cls: KClass<T>): LevelConverter<T> {
            val converter = registry[cls]
            check(converter != null) {
                "Unable to convert log level of class `${cls.qualifiedName}`." +
                        " No converter was found." +
                        " Please call `LevelConverter.register()` for adding the conversion."
            }
            @Suppress("UNCHECKED_CAST") // Safe as we put a bounded type `T`.
            return converter as LevelConverter<T>
        }

        /**
         * Obtains a converter that translates levels of type [T] to [Level].
         */
        public fun <T: Any> reverse(cls: KClass<T>): Converter<T, Level> = get(cls).reverse

        /**
         * Adds a converter for levels of the type [T].
         *
         * @return previously registered converter, if any.
         */
        public fun <T : Any> register(
            cls: KClass<T>,
            converter: LevelConverter<T>
        ): LevelConverter<T>? {
            val prevEntry = registry.put(cls, converter)
            @Suppress("UNCHECKED_CAST") // safe as the key is a bounded type `T`.
            return prevEntry as LevelConverter<T>?
        }

        /**
         * Converts the given level to the level of type [T].
         */
        public inline fun <reified T: Any> convert(level: Level): T = get(T::class)(level)

        /**
         * Converts the given level of type [T] to [Level].
         */
        public inline fun <reified T: Any> reverse(level: T): Level = reverse(T::class)(level)
    }
}
