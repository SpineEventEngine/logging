/*
 * Copyright (C) 2020 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.google.common.collect.Iterators
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.testing.FakeMetadata
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal abstract class MetadataProcessorSpec(private val factory: ProcessorFactory) {

    companion object {
        private val KEY_1 = single("K1", String::class.java)
        private val KEY_2 = single("K2", String::class.java)
        private val KEY_3 = single("K3", String::class.java)
        private val REP_1 = repeated("R1", String::class.java)
        private val REP_2 = repeated("R2", String::class.java)
    }

    @Test
    fun `combine scope and log site metadata with singleton keys`() {
        val scope = FakeMetadata().add(KEY_1, "one").add(KEY_2, "two")
        val logged = FakeMetadata().add(KEY_3, "three")
        val metadata = factory.processorFor(scope, logged)
        entries(metadata) shouldContainExactly listOf("K1=one", "K2=two", "K3=three")
        metadata.keySet() shouldContainExactly setOf(KEY_1, KEY_2, KEY_3)
        metadata.getSingleValue(KEY_1) shouldBe "one"
        metadata.getSingleValue(KEY_3) shouldBe "three"
    }

    @Test
    fun `combine scope and log site metadata with mixed keys`() {
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
    fun `combine scope and log site metadata with overriding and duplicating keys`() {
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
        val expected = listOf("K1=override", "R1=[r1-1, r1-2]", "R2=[r2-1, r2-2, r2-1]", "K2=value")
        entries(metadata) shouldContainExactly expected
        metadata.keySet() shouldContainExactly setOf(KEY_1, REP_1, REP_2, KEY_2)
    }

    @Test
    fun `process the maximum number of entries with shareable keys`() {
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
    fun `process the maximum number of entries with distinct keys`() {
        val scope = FakeMetadata()
        repeat(28) { i -> // 28 is a max number of entries for the lightweight processor.
            val key = single("K$i", String::class.java)
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

    @Test
    fun `withstand worst case performance scenario`() {
        // Since duplicated keys need to have their index looked up (linear scan),
        // the worst case scenario for performance is 14 distinct keys, followed by
        // the same repeated key 14 times. This means (N/2)^2 key accesses.
        val scope = FakeMetadata()
        for (n in 0..13) {
            val key = single("K$n", String::class.java)
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
        val handler: MetadataHandler<Void> = object : MetadataHandler<Void>() {
            override fun <T> handle(key: MetadataKey<T>, value: T, context: Void) = Unit
            override fun <T> handleRepeated(key: MetadataKey<T>,
                                            values: MutableIterator<T>,
                                            context: Void?) {
                values.hasNext().shouldBeTrue()
                values.next() shouldBe "one"
                values.remove()
            }
        }
        shouldThrow<UnsupportedOperationException> {
            metadata.process(handler, null)
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
    metadata.process(COLLECTING_HANDLER, entries)
    return entries
}

/**
 * Processes the given [metadata] for a single metadata [key],
 * returning the formatted entry.
 */
private fun handleEntry(metadata: MetadataProcessor, key: MetadataKey<*>): String? {
    val entries = arrayListOf<String>()
    metadata.handle(key, COLLECTING_HANDLER, entries)
    entries.size shouldBeLessThanOrEqual 1
    return entries.firstOrNull()
}

private object COLLECTING_HANDLER : MetadataHandler<MutableList<String>>() {

    override fun <T : Any?> handle(key: MetadataKey<T>, value: T, out: MutableList<String>) {
        val stringified = "%s=%s".format(key.label, value)
        out.add(stringified)
    }

    override fun <T : Any?> handleRepeated(key: MetadataKey<T>,
                                           values: MutableIterator<T>,
                                           out: MutableList<String>) {
        val stringified = "%s=%s".format(key.label, Iterators.toString(values))
        out.add(stringified)
    }

}
