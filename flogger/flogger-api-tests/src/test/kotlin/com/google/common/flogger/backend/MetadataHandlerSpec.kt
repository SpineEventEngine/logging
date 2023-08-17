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

package com.google.common.flogger.backend

import com.google.common.base.Joiner
import com.google.common.collect.Iterators
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [MetadataHandler].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/backend/MetadataHandlerTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`MetadataHandler` should")
internal class MetadataHandlerSpec {

    @Test
    fun `use a default singleton value handler for unknown keys`() {
        val unknownKey = single("unknown", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .build()

        val logged = Metadata.empty()
        val scope = FakeMetadata().add(unknownKey, "hello")

        process(handler, scope, logged) shouldBe "unknown=<<hello>>"
    }

    @Test
    fun `use a singleton value handler for any keys`() {
        val key = single("key", String::class.java)
        val rep = repeated("rep", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .addHandler(key, ::appendValue)
            .addHandler(rep, ::appendValue)
            .build()

        val logged = Metadata.empty()
        val scope = FakeMetadata()
            .add(key, "hello")
            .add(rep, "repeated")
            .add(rep, "world")

        process(handler, scope, logged) shouldBe "key=hello rep=repeated rep=world"
    }

    @Test
    fun `use a repeated value handler for repeated keys`() {
        val key = repeated("key", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .addRepeatedHandler(key, ::appendValues)
            .build()

        val logged = Metadata.empty()
        val scope = FakeMetadata()
            .add(key, "hello")
            .add(key, "world")

        process(handler, scope, logged) shouldBe "key=[hello, world]"
    }

    @Test
    fun `use key-specific handlers over the default ones`() {

        /*
        `barKey` uses `Int::class.javaObjectType` to make sure we get `Integer` class on JVM.
        Otherwise, Kotlin compiler passes `int` class for primitives. It is important because
        metadata objects are generified, which means they would use boxed primitives.
        */
        val barKey = repeated("bar", Int::class.javaObjectType)
        val fooKey = repeated("foo", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .setDefaultRepeatedHandler(::appendUnknownValues)
            .addHandler(fooKey, ::appendValue) // Explicit individual handler takes precedence.
            .build()

        val scope = FakeMetadata()
            .add(fooKey, "hello")
            .add(barKey, 13)
            .add(barKey, 20)
        val logged = FakeMetadata()
            .add(fooKey, "world")
            .add(barKey, 9)

        process(handler, scope, logged) shouldBe "foo=hello foo=world bar=<<13, 20, 9>>"
    }

    @Test
    fun `use multiple value handlers`() {

        /*
        `barKey` uses `Int::class.javaObjectType` to make sure we get `Integer` class on JVM.
        Otherwise, Kotlin compiler passes `int` class for primitives. It is important because
        metadata objects are generified, which means they would use boxed primitives.
        */
        val barKey = repeated("bar", Int::class.javaObjectType)
        val fooKey = repeated("foo", String::class.java)
        val unknownKey = single("unknown", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .addRepeatedHandler(barKey, ::appendSum)
            .addHandler(fooKey, ::appendValue)
            .build()

        val scope = FakeMetadata()
            .add(fooKey, "hello")
            .add(barKey, 13)
            .add(barKey, 20)
            .add(unknownKey, "ball")
        val logged = FakeMetadata()
            .add(fooKey, "world")
            .add(barKey, 9)

        process(handler, scope, logged) shouldBe "foo=hello foo=world sum(bar)=42 unknown=<<ball>>"
    }

    @Test
    fun `use the latest specified handler`() {
        val foo = repeated("foo", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .addRepeatedHandler(foo, ::appendValues)
            .addHandler(foo, ::appendValue)
            .build()

        val logged = Metadata.empty()
        val scope = FakeMetadata()
            .add(foo, "hello")
            .add(foo, "world")

        process(handler, scope, logged) shouldBe "foo=hello foo=world"
    }

    @Test
    fun `remove previously specified handlers`() {
        val foo = repeated("foo", String::class.java)
        val builder = MetadataHandler.builder(::appendUnknownValue)
            .addRepeatedHandler(foo, ::appendValues)

        val logged = Metadata.empty()
        val scope = FakeMetadata()
            .add(foo, "hello")
            .add(foo, "world")

        val withFooHandler = builder.build()
        process(withFooHandler, scope, logged) shouldBe "foo=[hello, world]"

        val withoutFooHandler = builder.removeHandlers(foo).build()
        process(withoutFooHandler, scope, logged) shouldBe "foo=<<hello>> foo=<<world>>"
    }
}

/**
 * Combines the given [scope] and [logged] metadata into a unified view,
 * invoking the given handler for each distinct metadata key.
 *
 * Please note, many tests pass [Metadata.empty] to [logged] but it is not
 * specified for the default value for this parameter. It is better to pass
 * it explicitly because it is an essential parameter when it comes to merging
 * two metadata objects.
 *
 * @return string representation of the resulting view
 */
private fun process(
    handler: MetadataHandler<StringBuilder>, scope: Metadata, logged: Metadata
): String {
    val out = StringBuilder()
    val processor = MetadataProcessor.forScopeAndLogSite(scope, logged)
    processor.process(handler, out)
    return "$out".trim()
}

/**
 * Serves as a default handler for singleton values.
 *
 * @see MetadataHandler.builder
 */
private fun appendUnknownValue(key: MetadataKey<*>, value: Any, out: StringBuilder) {
    out.append("${key.label}=<<$value>> ")
}

/**
 * Serves as a default handler for repeated values.
 *
 * @see MetadataHandler.builder
 */
private fun appendUnknownValues(key: MetadataKey<*>, values: Iterator<*>, out: StringBuilder) {
    val joinedValues = Joiner.on(", ").join(values)
    appendUnknownValue(key, joinedValues, out)
}

private fun appendValue(key: MetadataKey<*>, value: Any, out: StringBuilder) {
    out.append("${key.label}=$value ")
}

private fun appendValues(key: MetadataKey<*>, values: Iterator<*>, out: StringBuilder) {
    val joinedValues = Iterators.toString(values)
    appendValue(key, joinedValues, out)
}

private fun appendSum(key: MetadataKey<Int>, values: Iterator<Int>, out: StringBuilder) {
    var sum = 0
    values.forEach { sum += it }
    out.append("sum(${key.label})=$sum ")
}
