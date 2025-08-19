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

import io.spine.logging.MetadataKey as CommonMetadataKey
import kotlin.reflect.KClass

// Backward-compatible typealias: former JVM class becomes an alias to the new common class.
public typealias MetadataKey<T> = CommonMetadataKey<T>

// Deprecated helpers to minimize churn in existing code/tests.
@Deprecated(
    message = "Use io.spine.logging.MetadataKey.single(label, clazz).",
    replaceWith = ReplaceWith("io.spine.logging.MetadataKey.single(label, clazz)")
)
public fun <T : Any> single(label: String, clazz: KClass<out T>): MetadataKey<T> =
    CommonMetadataKey.single(label, clazz)

@Deprecated(
    message = "Use io.spine.logging.MetadataKey.repeated(label, clazz).",
    replaceWith = ReplaceWith("io.spine.logging.MetadataKey.repeated(label, clazz)")
)
public fun <T : Any> repeated(label: String, clazz: KClass<out T>): MetadataKey<T> =
    CommonMetadataKey.repeated(label, clazz)

// Kotlin-friendly helpers retained for existing tests and call-sites.
@Deprecated(
    message = "Use io.spine.logging.MetadataKey.single<T>(label).",
    replaceWith = ReplaceWith("io.spine.logging.MetadataKey.single<T>(label)")
)
public inline fun <reified T : Any> singleKey(label: String): MetadataKey<T> =
    CommonMetadataKey.single(label)

@Deprecated(
    message = "Use io.spine.logging.MetadataKey.repeated<T>(label).",
    replaceWith = ReplaceWith("io.spine.logging.MetadataKey.repeated<T>(label)")
)
public inline fun <reified T : Any> repeatedKey(label: String): MetadataKey<T> =
    CommonMetadataKey.repeated(label)

// Backward-compatible checkCannotRepeat function
@Deprecated(
    message = "Use io.spine.logging.checkCannotRepeat(key).",
    replaceWith = ReplaceWith("io.spine.logging.checkCannotRepeat(key)")
)
public fun checkCannotRepeat(key: MetadataKey<*>) {
    io.spine.logging.checkCannotRepeat(key)
}
