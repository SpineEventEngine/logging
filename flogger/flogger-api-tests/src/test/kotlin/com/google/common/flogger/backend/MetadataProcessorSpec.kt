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

import com.google.common.collect.Iterables
import com.google.common.collect.Iterators
import com.google.common.flogger.MetadataKey
import com.google.common.flogger.testing.FakeMetadata
import com.google.common.truth.Truth.assertThat
import java.util.*
import java.util.function.BiFunction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.runners.Parameterized

internal abstract class MetadataProcessorSpec(private val factory: ProcessorFactory) {

    @Test
    fun testSimpleCombinedMetadata() {
        val scope = FakeMetadata()
        scope.add(KEY_1, "one")
        scope.add(KEY_2, "two")
        val logged = FakeMetadata()
        logged.add(KEY_3, "three")
        val metadata = factory.apply(scope, logged)
        assertThat(extractEntries(metadata)).containsExactly("K1=one", "K2=two", "K3=three")
            .inOrder()
        assertThat(metadata!!.keyCount()).isEqualTo(3)
        assertThat(metadata.keySet()).containsExactly(KEY_1, KEY_2, KEY_3).inOrder()
        assertThat(metadata.getSingleValue(KEY_1)).isEqualTo("one")
        assertThat(metadata.getSingleValue(KEY_3)).isEqualTo("three")
    }

    @Test
    fun testSimpleRepeated() {
        val scope = FakeMetadata()
        scope.add(REP_1, "first")
        scope.add(KEY_1, "single")
        scope.add(REP_1, "second")
        val logged = FakeMetadata()
        logged.add(REP_1, "third")
        val metadata = factory.apply(scope, logged)
        assertThat(extractEntries(metadata))
            .containsExactly("R1=[first, second, third]", "K1=single")
            .inOrder()
        assertThat(metadata!!.keyCount()).isEqualTo(2)
        assertThat(metadata.keySet()).containsExactly(REP_1, KEY_1).inOrder()
        assertThat(metadata.getSingleValue(KEY_1)).isEqualTo("single")
        try {
            metadata.getSingleValue(REP_1)
            Assertions.fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // pass
        }
    }

    @Test
    fun testMessy() {
        val scope = FakeMetadata()
        scope.add(KEY_1, "original")
        scope.add(REP_1, "r1-1")
        scope.add(REP_2, "r2-1")
        scope.add(REP_1, "r1-2")
        val logged = FakeMetadata()
        logged.add(REP_2, "r2-2")
        logged.add(KEY_2, "value")
        logged.add(REP_2, "r2-1") // Duplicated from scope.
        logged.add(KEY_1, "override")
        val metadata = factory.apply(scope, logged)
        assertThat(extractEntries(metadata))
            .containsExactly("K1=override", "R1=[r1-1, r1-2]", "R2=[r2-1, r2-2, r2-1]", "K2=value")
            .inOrder()
        assertThat(metadata!!.keyCount()).isEqualTo(4)
        assertThat(metadata.keySet()).containsExactly(KEY_1, REP_1, REP_2, KEY_2).inOrder()
    }

    @Test
    fun testMaxLightweight() {
        // Max entries is 28 for lightweight processor.
        val scope = FakeMetadata()
        for (n in 0..27) {
            val k = if (n and 1 == 0) REP_1 else REP_2
            scope.add(k, "v$n")
        }
        val metadata = factory.apply(scope, Metadata.empty())
        assertThat(extractEntries(metadata))
            .containsExactly(
                "R1=[v0, v2, v4, v6, v8, v10, v12, v14, v16, v18, v20, v22, v24, v26]",
                "R2=[v1, v3, v5, v7, v9, v11, v13, v15, v17, v19, v21, v23, v25, v27]"
            )
            .inOrder()
    }

    @Test
    fun testAllDistinctKeys() {
        // Max entries is 28 for lightweight processor. With all distinct keys you are bound to force
        // at least one false positive in the bloom filter in the lightweight processor.
        val scope = FakeMetadata()
        for (n in 0..27) {
            scope.add(
                MetadataKey.single(
                    "K$n",
                    String::class.java
                ), "v$n"
            )
        }
        val metadata = factory.apply(scope, Metadata.empty())
        assertThat(extractEntries(metadata))
            .containsExactly(
                "K0=v0",
                "K1=v1",
                "K2=v2",
                "K3=v3",
                "K4=v4",
                "K5=v5",
                "K6=v6",
                "K7=v7",
                "K8=v8",
                "K9=v9",
                "K10=v10",
                "K11=v11",
                "K12=v12",
                "K13=v13",
                "K14=v14",
                "K15=v15",
                "K16=v16",
                "K17=v17",
                "K18=v18",
                "K19=v19",
                "K20=v20",
                "K21=v21",
                "K22=v22",
                "K23=v23",
                "K24=v24",
                "K25=v25",
                "K26=v26",
                "K27=v27"
            )
            .inOrder()
    }

    @Test
    fun testWorstCaseLookup() {
        // Since duplicated keys need to have their index looked up (linear scan) the worst case
        // scenario for performance in 14 distinct keys followed by the same repeated key 14 times.
        // This means that there are (N/2)^2 key accesses.
        val scope = FakeMetadata()
        for (n in 0..13) {
            scope.add(
                MetadataKey.single(
                    "K$n",
                    String::class.java
                ), "v$n"
            )
        }
        for (n in 14..27) {
            scope.add(REP_1, "v$n")
        }
        val metadata = factory.apply(scope, Metadata.empty())
        assertThat(extractEntries(metadata))
            .containsExactly(
                "K0=v0",
                "K1=v1",
                "K2=v2",
                "K3=v3",
                "K4=v4",
                "K5=v5",
                "K6=v6",
                "K7=v7",
                "K8=v8",
                "K9=v9",
                "K10=v10",
                "K11=v11",
                "K12=v12",
                "K13=v13",
                "R1=[v14, v15, v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27]"
            )
            .inOrder()
    }

    @Test
    fun testSingleKeyHandling() {
        val scope = FakeMetadata()
        scope.add(REP_1, "first")
        scope.add(KEY_1, "single")
        scope.add(REP_1, "second")
        val logged = FakeMetadata()
        logged.add(REP_1, "third")
        val metadata = factory.apply(scope, logged)
        assertThat(handleEntry(metadata, REP_1)).isEqualTo("R1=[first, second, third]")
        assertThat(handleEntry(metadata, KEY_1)).isEqualTo("K1=single")
        assertThat(handleEntry(metadata, KEY_3)).isNull()
    }

    @Test
    fun testReadOnlyIterable() {
        val scope = FakeMetadata()
        scope.add(REP_1, "one")
        scope.add(REP_1, "two")
        val metadata = factory.apply(scope, Metadata.empty())
        val handler: MetadataHandler<Void> = object : MetadataHandler<Void>() {
            override fun <T> handle(key: MetadataKey<T>, value: T, context: Void) {}
            override fun <T> handleRepeated(
                key: MetadataKey<T>,
                values: MutableIterator<T>,
                context: Void?
            ) {
                assertThat(values.hasNext()).isTrue()
                assertThat(values.next()).isEqualTo("one")
                values.remove()
            }
        }
        try {
            metadata!!.process(handler, null)
            Assertions.fail("expected UnsupportedOperationException")
        } catch (expected: UnsupportedOperationException) {
            // pass
        }
    }
}

fun interface ProcessorFactory : BiFunction<Metadata?, Metadata?, MetadataProcessor?>

private val KEY_1 = MetadataKey.single(
    "K1",
    String::class.java
)
private val KEY_2 = MetadataKey.single(
    "K2",
    String::class.java
)
private val KEY_3 = MetadataKey.single(
    "K3",
    String::class.java
)
private val REP_1 = MetadataKey.repeated(
    "R1",
    String::class.java
)
private val REP_2 = MetadataKey.repeated(
    "R2",
    String::class.java
)

@Parameterized.Parameters(name = "{1}")
fun factories(): Collection<Array<Any>> {
    return listOf(
        arrayOf(ProcessorFactory { scope: Metadata?, logged: Metadata? ->
            MetadataProcessor.getLightweightProcessor(
                scope,
                logged
            )
        }, "Lightweight Processor"), arrayOf(
            ProcessorFactory { scope: Metadata?, logged: Metadata? ->
                MetadataProcessor.getSimpleProcessor(
                    scope,
                    logged
                )
            }, "Simple Processor"
        )
    )
}

// Processes all metadata, collecting formatted results as strings.
private fun extractEntries(metadata: MetadataProcessor?): List<String> {
    val entries: MutableList<String> = ArrayList()
    metadata!!.process(COLLECTING_HANDLER, entries)
    return entries
}

// Handles a single metadata entry, returning null if the key is not present.
private fun handleEntry(metadata: MetadataProcessor?, key: MetadataKey<*>): String? {
    val entries: MutableList<String> = ArrayList()
    metadata!!.handle(key, COLLECTING_HANDLER, entries)
    assertThat(entries.size).isAtMost(1)
    return Iterables.getFirst(entries, null)
}

private val COLLECTING_HANDLER: MetadataHandler<MutableList<String>> =
    object : MetadataHandler<MutableList<String>>() {
        override fun <T> handle(
            key: MetadataKey<T>,
            value: T,
            out: MutableList<String>
        ) {
            out.add(String.format("%s=%s", key.label, value))
        }

        override fun <T> handleRepeated(
            key: MetadataKey<T>, values: Iterator<T>, out: MutableList<String>
        ) {
            out.add(String.format("%s=%s", key.label, Iterators.toString(values)))
        }
    }
