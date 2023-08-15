/*
 * Copyright (C) 2020 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

@DisplayName("`MetadataHandler` should")
internal class MetadataHandlerSpec {

    @Test
    fun `use a default singleton value handler for unknown keys`() {
        val unknownKey = single("unknown", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .build()

        val logged = Metadata.empty()
        val scope = FakeMetadata().apply {
            add(unknownKey, "hello")
        }

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
        val scope = FakeMetadata().apply {
            add(key, "hello")
            add(rep, "repeated")
            add(rep, "world")
        }

        process(handler, scope, logged) shouldBe "key=hello rep=repeated rep=world"
    }

    @Test
    fun `use a repeated value handler for repeated keys`() {
        val key = repeated("key", String::class.java)
        val handler = MetadataHandler.builder(::appendUnknownValue)
            .addRepeatedHandler(key, ::appendValues)
            .build()

        val logged = Metadata.empty()
        val scope = FakeMetadata().apply {
            add(key, "hello")
            add(key, "world")
        }

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

        val scope = FakeMetadata().apply {
            add(fooKey, "hello")
            add(barKey, 13)
            add(barKey, 20)
        }
        val logged = FakeMetadata().apply {
            add(fooKey, "world")
            add(barKey, 9)
        }

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

        val scope = FakeMetadata().apply {
            add(fooKey, "hello")
            add(barKey, 13)
            add(barKey, 20)
            add(unknownKey, "ball")
        }
        val logged = FakeMetadata().apply {
            add(fooKey, "world")
            add(barKey, 9)
        }

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
        val scope = FakeMetadata().apply {
            add(foo, "hello")
            add(foo, "world")
        }

        process(handler, scope, logged) shouldBe "foo=hello foo=world"
    }

    @Test
    fun `remove previously specified handlers`() {
        val foo = repeated("foo", String::class.java)
        val builder = MetadataHandler.builder(::appendUnknownValue)
            .addRepeatedHandler(foo, ::appendValues)

        val logged = Metadata.empty()
        val scope = FakeMetadata().apply {
            add(foo, "hello")
            add(foo, "world")
        }

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
