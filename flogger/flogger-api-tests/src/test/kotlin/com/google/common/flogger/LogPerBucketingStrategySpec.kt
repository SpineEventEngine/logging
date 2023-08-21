/*
 * Copyright (C) 2023 The Flogger Authors.
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
package com.google.common.flogger

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Please note this test suite doesn't use static imports for [LogPerBucketingStrategy]
 * methods because they clash with Kotlin's [built-in][apply] extension.
 *
 * [LogPerBucketingStrategy] declares a method named `apply`.
 */
@DisplayName("`LogPerBucketingStrategy` should provide a strategy that")
internal class LogPerBucketingStrategySpec {

    @Test
    fun `passes keys 'as is'`() {
        val anyKey = Any()
        val strategy = LogPerBucketingStrategy.knownBounded()
        strategy.apply(anyKey) shouldBeSameInstanceAs anyKey
        "$strategy" shouldBe "LogPerBucketingStrategy[KnownBounded]"
    }

    @Test
    fun `aggregates keys by class`() {
        val anyKey = Any()
        val strategy = LogPerBucketingStrategy.byClass()
        strategy.apply(anyKey) shouldBeSameInstanceAs anyKey.javaClass
        "$strategy" shouldBe "LogPerBucketingStrategy[ByClass]"
    }

    @Test
    fun `aggregates keys by class name`() {
        class NotASystemClass
        val anyKey = NotASystemClass()
        val className = NotASystemClass::class.java.name
        val strategy = LogPerBucketingStrategy.byClassName()
        strategy.apply(anyKey) shouldBeSameInstanceAs className
        "$strategy" shouldBe "LogPerBucketingStrategy[ByClassName]"
    }

    @Test
    fun `maps keys to a pre-defines set of identifiers`() {
        val strategy = LogPerBucketingStrategy.forKnownKeys(mutableListOf("foo", 23))
        strategy.apply("foo") shouldBe 0
        strategy.apply("bar").shouldBeNull()
        strategy.apply(23) shouldBe 1
        strategy.apply(23.0).shouldBeNull()
        "$strategy" shouldBe "LogPerBucketingStrategy[ForKnownKeys(foo, 23)]"
    }

    @Test
    fun `aggregates keys by hash code`() {
        val key: Any = object : Any() {
            override fun hashCode(): Int = -1
        }
        val strategy = { maxBuckets: Int -> LogPerBucketingStrategy.byHashCode(maxBuckets) }
        // Show that the strategy choice changes the bucketed value as expected. To maximize
        // Integer caching done by the JVM, the expected value has 128 subtracted from it.
        strategy(1).apply(key) shouldBeSameInstanceAs -128
        strategy(30).apply(key) shouldBeSameInstanceAs (29 - 128)
        strategy(10).apply(key) shouldBeSameInstanceAs (9 - 128)
        // Max cached value is 127 (corresponding to a modulo of 255).
        strategy(256).apply(key) shouldBeSameInstanceAs 127
        // Above this we cannot assume singleton semantics.
        strategy(257).apply(key) shouldBe 128
        "${strategy(10)}" shouldBe "LogPerBucketingStrategy[ByHashCode(10)]"
    }
}
