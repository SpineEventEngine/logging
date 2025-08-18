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

package io.spine.logging.backend

import com.google.common.collect.Iterators
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.logging.backend.given.FakeMetadata
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.repeatedKey
import io.spine.logging.jvm.singleKey
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [MetadataProcessor].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/MetadataProcessorTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@Suppress("unused")
internal abstract class MetadataProcessorSpec(private val factory: ProcessorFactory) {

    companion object {
        private val KEY_1 = singleKey<Any>("K1")
        private val KEY_2 = singleKey<Any>("K2")
        private val KEY_3 = singleKey<Any>("K3")
        private val REP_1 = repeatedKey<Any>("R1")
        private val REP_2 = repeatedKey<Any>("R2")
    }

    @Nested inner class
    `combine scope and log site metadata` {

        @Test
        fun `with singleton keys`() {
            val scope = FakeMetadata().add(KEY_1, "one").add(KEY_2, "two")
            val logged = FakeMetadata().add(KEY_3, "three")
            val metadata = factory.processorFor(scope, logged)
            entries(metadata) shouldContainExactly listOf("K1=one", "K2=two", "K3=three")
            metadata.keySet() shouldContainExactly setOf(KEY_1, KEY_2, KEY_3)
            metadata.getSingleValue(KEY_1) shouldBe "one"
            metadata.getSingleValue(KEY_3) shouldBe "three"
        }

        @Test
        fun `with mixed keys`() {
            val scope = FakeMetadata()
                .add(REP_1, "first")
                .add(KEY_1, "single")
                .add(REP_1, "second")
            val logged = FakeMetadata().add(REP_1, "third")
            val metadata = factory.processorFor(scope, logged)
            entries(metadata) shouldContainExactly listOf("R1=[first, second, third]", "K1=single")
            metadata.keySet() shouldContainExactly setOf(REP_1, KEY_1)
            metadata.getSingleValue(KEY_1) shouldBe "single"
            shouldThrow<IllegalArgumentException> {
                metadata.getSingleValue(REP_1)
            }
        }

        @Test
        fun `with overriding and duplicating keys`() {
            val scope = FakeMetadata()
                .add(KEY_1, "original")
                .add(REP_1, "r1-1")
                .add(REP_2, "r2-1")
                .add(REP_1, "r1-2")
            val logged = FakeMetadata()
                .add(REP_2, "r2-2")
                .add(KEY_2, "value")
                .add(REP_2, "r2-1") // Duplicate.
                .add(KEY_1, "override") // Override.
            val metadata = factory.processorFor(scope, logged)
            val expected = listOf(
                "K1=override", "R1=[r1-1, r1-2]",
                "R2=[r2-1, r2-2, r2-1]", "K2=value"
            )
            entries(metadata) shouldContainExactly expected
            metadata.keySet() shouldContainExactly setOf(KEY_1, REP_1, REP_2, KEY_2)
        }
    }

    @Nested inner class
    `process the maximum number of entries` {

        @Test
        fun `with shareable keys`() {
            val scope = FakeMetadata()
            repeat(28) { i -> // 28 is a max number of entries for the lightweight processor.
                val key = if (i and 1 == 0) REP_1 else REP_2
                scope.add(key, "v$i")
            }
            val metadata = factory.processorFor(scope, Metadata.empty())
            entries(metadata) shouldContainExactly listOf(
                "R1=[v0, v2, v4, v6, v8, v10, v12, v14, v16, v18, v20, v22, v24, v26]",
                "R2=[v1, v3, v5, v7, v9, v11, v13, v15, v17, v19, v21, v23, v25, v27]"
            )
        }

        @Test
        fun `with distinct keys`() {
            val scope = FakeMetadata()
            repeat(28) { i -> // 28 is a max number of entries for the lightweight processor.
                val key = singleKey<String>("K$i")
                val value = "v$i"
                scope.add(key, value)
            }
            val metadata = factory.processorFor(scope, Metadata.empty())
            entries(metadata) shouldContainExactly listOf(
                "K0=v0", "K1=v1", "K2=v2", "K3=v3", "K4=v4", "K5=v5", "K6=v6",
                "K7=v7", "K8=v8", "K9=v9", "K10=v10", "K11=v11", "K12=v12", "K13=v13",
                "K14=v14", "K15=v15", "K16=v16", "K17=v17", "K18=v18", "K19=v19", "K20=v20",
                "K21=v21", "K22=v22", "K23=v23", "K24=v24", "K25=v25", "K26=v26", "K27=v27"
            )
        }
    }

    @Test
    fun `withstand worst case performance scenario`() {
        // Since duplicated keys need to have their index looked up (linear scan),
        // the worst case scenario for performance is 14 distinct keys, followed by
        // the same repeated key 14 times. This means (N/2)^2 key accesses.
        val scope = FakeMetadata()
        for (n in 0..13) {
            val key = singleKey<String>("K$n")
            val value = "v$n"
            scope.add(key, value)
        }
        for (n in 14..27) {
            scope.add(REP_1, "v$n")
        }
        val metadata = factory.processorFor(scope, Metadata.empty())
        entries(metadata) shouldContainExactly listOf(
            "K0=v0", "K1=v1", "K2=v2", "K3=v3", "K4=v4", "K5=v5", "K6=v6", "K7=v7",
            "K8=v8", "K9=v9", "K10=v10", "K11=v11", "K12=v12", "K13=v13",
            "R1=[v14, v15, v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27]"
        )
    }

    @Test
    fun `handle a duplicate key`() {
        val scope = FakeMetadata()
            .add(REP_1, "first")
            .add(KEY_1, "single")
            .add(REP_1, "second")
        val logged = FakeMetadata().add(REP_1, "third")
        val metadata = factory.processorFor(scope, logged)
        handleEntry(metadata, REP_1) shouldBe "R1=[first, second, third]"
        handleEntry(metadata, KEY_1) shouldBe "K1=single"
        handleEntry(metadata, KEY_3).shouldBeNull()
    }

    @Test
    fun `use an immutable iterator for repeated keys`() {
        val scope = FakeMetadata()
            .add(REP_1, "one")
            .add(REP_1, "two")
        val metadata = factory.processorFor(scope, Metadata.empty())
        val handler: MetadataHandler<String> = object : MetadataHandler<String>() {
            override fun <T : Any> handle(key: MetadataKey<T>, value: T, context: String) = Unit
            override fun <T : Any> handleRepeated(key: MetadataKey<T>,
                                            values: Iterator<T>,
                                            context: String) {
                values.hasNext().shouldBeTrue()
                values.next() shouldBe "one"
                (values as MutableIterator).remove()
            }
        }
        shouldThrow<UnsupportedOperationException> {
            metadata.process(handler, "")
        }
    }
}

/**
 * A convenience interface to describe [MetadataProcessor.getLightweightProcessor]
 * and [MetadataProcessor.getSimpleProcessor] methods' signature.
 */
fun interface ProcessorFactory {
    fun processorFor(logged: Metadata, scope: Metadata): MetadataProcessor
}

/**
 * Processes the given [metadata], collecting the all formatted entries as strings.
 */
private fun entries(metadata: MetadataProcessor): List<String> {
    val entries = arrayListOf<String>()
    metadata.process(CollectingHandler, entries)
    return entries
}

/**
 * Processes the given [metadata] for a single metadata [key],
 * returning the formatted entry.
 */
private fun handleEntry(metadata: MetadataProcessor, key: MetadataKey<Any>): String? {
    val entries = arrayListOf<String>()
    metadata.handle(key, CollectingHandler, entries)
    entries.size shouldBeLessThanOrEqual 1
    return entries.firstOrNull()
}

private object CollectingHandler : MetadataHandler<MutableList<String>>() {

    override fun <T : Any> handle(
        key: MetadataKey<T>,
        value: T,
        context: MutableList<String>
    ) {
        val stringified = "%s=%s".format(key.label, value)
        context.add(stringified)
    }

    override fun <T : Any> handleRepeated(
        key: MetadataKey<T>,
        values: Iterator<T>,
        context: MutableList<String>
    ) {
        val stringified = "%s=%s".format(key.label, Iterators.toString(values))
        context.add(stringified)
    }
}
