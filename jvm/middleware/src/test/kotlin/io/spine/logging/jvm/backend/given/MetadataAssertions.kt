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

package io.spine.logging.jvm.backend.given

import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.backend.Metadata
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * This file contains Kotest-like assertions for [Metadata].
 *
 * They are needed because [Metadata] doesn't extend [Collection] itself,
 * or any of its inheritors.
 */
@Suppress("unused") // It is used for file-level docs.
private val about = ""

/**
 * Asserts that this [Metadata] doesn't have any key/value pairs.
 */
internal fun Metadata.shouldBeEmpty() {
    size() shouldBeExactly 0
}

/**
 * Asserts that this [Metadata] has the given [number] of key/value pairs.
 */
internal infix fun Metadata.shouldHaveSize(number: Int) {
    size() shouldBeExactly number
}

/**
 * Asserts that this [Metadata] has a [key] with the mapped [values].
 */
internal fun <T : Any> Metadata.shouldContainInOrder(key: MetadataKey<T>, vararg values: T) {
    valuesOf(key) shouldContainInOrder values.asList()
}

/**
 * Asserts that this [Metadata] has a [key] with the given [value].
 *
 * The given [value] should be the first one, which was mapped to the [key].
 */
internal fun <T : Any> Metadata.shouldHaveFirstValue(key: MetadataKey<T>, value: T) {
    findValue(key) shouldBe value
}

/**
 * Asserts that this [Metadata] does NOT HAVE a value for the given [key].
 */
internal infix fun <T : Any> Metadata.shouldNotContain(key: MetadataKey<T>) {
    findValue(key).shouldBeNull()
}

/**
 * Asserts that this [Metadata] has one or more values for the given [key]
 */
internal infix fun <T : Any> Metadata.shouldContain(key: MetadataKey<T>) {
    findValue(key).shouldNotBeNull()
}

/**
 * Asserts that this [Metadata] has a [key] to which only a single [value] is mapped.
 */
internal fun <T : Any> Metadata.shouldUniquelyContain(key: MetadataKey<T>, value: T) {
    findValue(key) shouldBe value
    @Suppress("UNCHECKED_CAST") val allKeys: List<MetadataKey<T>> =
        (0..<size()).map { i -> (getKey(i) as T) }.toList() as List<MetadataKey<T>>
    allKeys.indexOf(key) shouldBe allKeys.lastIndexOf(key)
}

private fun <T : Any> Metadata.valuesOf(key: MetadataKey<T>): List<T> {
    val values: MutableList<T> = ArrayList()
    for (n in 0..<size()) {
        if (getKey(n) == key) {
            values.add(key.cast(getValue(n))!!)
        }
    }
    return values
}
