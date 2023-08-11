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

package com.google.common.flogger.context.given

import com.google.common.flogger.MetadataKey
import com.google.common.flogger.backend.Metadata
import com.google.common.flogger.context.ContextMetadata
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * This file contains Kotest-like assertions for [Metadata].
 *
 * They are needed because [Metadata] doesn't extend [Collection] itself,
 * or any of its inheritors.
 */

fun ContextMetadata.shouldBeEmpty() {
    this.size() shouldBeExactly 0
}

infix fun ContextMetadata.shouldHaveSize(size: Int) {
    this.size() shouldBeExactly size
}

fun <T> ContextMetadata.shouldContainInOrder(key: MetadataKey<T>, vararg values: T) {
    this.valuesOf(key) shouldContainInOrder values.asList()
}

fun <T> ContextMetadata.shouldHaveFirstValue(key: MetadataKey<T>, value: T) {
    this.findValue(key) shouldBe value
}

fun <T> ContextMetadata.shouldNotContain(key: MetadataKey<T>) {
    this.findValue(key).shouldBeNull()
}

fun <T> ContextMetadata.valuesOf(key: MetadataKey<T>): List<T> {
    val values: MutableList<T> = ArrayList()
    for (n in 0..<size()) {
        if (getKey(n) == key) {
            values.add(key.cast(getValue(n)))
        }
    }
    return values
}
