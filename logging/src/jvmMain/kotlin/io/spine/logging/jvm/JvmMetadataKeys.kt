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

package io.spine.logging.jvm

/**
 * Creates a new single [MetadataKey] with the given [label].
 *
 * In JVM, if the given type [T] describes a Java primitive,
 * this method would use a type of the corresponding object wrapper.
 * Thus, making type [T] safe to be used with Java generics, that is
 * the case for metadata keys.
 *
 * @param T type of values that can be associated with this key
 */
public inline fun <reified T : Any> singleKey(label: String): MetadataKey<T> =
    MetadataKey.single(label, T::class)

/**
 * Creates a new repeated [MetadataKey] with the given [label].
 *
 * In JVM, if the given type [T] describes a Java primitive,
 * this method would use a type of the corresponding object wrapper.
 * Thus, making type [T] safe to be used with Java generics, that is
 * the case for metadata keys.
 *
 * @param T type of values that can be associated with this key
 */
public inline fun <reified T : Any> repeatedKey(label: String): MetadataKey<T> =
    MetadataKey.repeated(label, T::class)
