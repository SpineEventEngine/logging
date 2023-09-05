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

package io.spine.logging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.reflect.given.InapplicableTestAnnotation
import io.spine.reflect.given.TestAnnotation
import io.spine.reflect.given.RepeatableTestAnnotation
import io.spine.reflect.given.nested1.Nested1
import io.spine.reflect.given.nested1.nested2.Nested2
import io.spine.reflect.given.nested1.nested2.nested3.Nested3
import io.spine.reflect.given.nested1.nested2.nested3.nested4.Nested4
import io.spine.reflect.given.unloaded.nested1.nested2.UnloadedNested2
import io.spine.reflect.given.unloaded.nested1.nested2.nested3.UnloadedNested3
import io.spine.reflect.given.unloaded.nested1.nested2.nested3.nested4.UnloadedNested4
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [PackageAnnotationLookup].
 *
 * This test suite uses the following hierarchy of test packages:
 *
 * ```
 * → nested1
 *   → nested2 [annotated!]
 *     → nested3
 *       → nested4
 * ```
 *
 * Each package contains its own anchor class named after the package:
 * [Nested1], [Nested2], etc.
 *
 * `nested2` is annotated with [TestAnnotation]. This annotation accepts
 * an anchor class. For `nested2` package it would be [Nested2].
 * So we can easily perform assertions:
 *
 * ```
 * // Checks if the given annotation belongs to the expected package.
 * annotation.anchor shouldBe Nested2.class
 * ```
 */
@DisplayName("`PackageAnnotationLookup` should")
internal class PackageAnnotationLookupSpec {

    private val annotationClass = TestAnnotation::class.java
    private val loadedPackages = MemoizingPackagesProvider()
    private val packageLoadings = MemoizingPackageLoader()
    private val lookup = PackageAnnotationLookup(
        annotationClass,
        loadedPackages::get,
        packageLoadings::load
    )

    @Nested inner class
    `throw when given` {

        @Test
        fun `a repeatable annotation`() {
            val repeatable = RepeatableTestAnnotation::class.java
            shouldThrow<IllegalArgumentException> {
                PackageAnnotationLookup(repeatable)
            }
        }

        @Test
        fun `a package-inapplicable annotation`() {
            val inapplicable = InapplicableTestAnnotation::class.java
            shouldThrow<IllegalArgumentException> {
                PackageAnnotationLookup(inapplicable)
            }
        }
    }

    @Nested inner class
    `return annotation if the package` {

        @Test
        fun `is directly annotated`() {
            val nested2 = Nested2::class
            val annotation = lookup.getFor(nested2.java.`package`)
            annotation.shouldNotBeNull()
            annotation.anchor shouldBe nested2
            loadedPackages.traversedTimes shouldBe 0
        }

        @Test
        fun `is indirectly annotated by a pre-loaded package`() {
            val nested4 = Nested4::class // `nested4` is NOT annotated.
            val nested2 = Nested2::class // Pre-loads annotated `nested2`.
            val annotation = lookup.getFor(nested4.java.`package`)
            annotation.shouldNotBeNull()
            annotation.anchor shouldBe nested2
            loadedPackages.traversedTimes shouldBe 1
        }

        /**
         * Tests how the lookup loads parental packages on its own.
         *
         * This test uses its own hierarchy of test packages, making sure
         * no one else would load them in advance. This hierarchy is similar
         * to the one used by other tests, but is located under `unloaded` package.
         * Anchor classes are also prefixed with “Unloaded” word.
         *
         * Gradle test task has also been configured to skip loading any members
         * from “unloaded” package hierarchy in advance.
         */
        @Test
        fun `is indirectly annotated by an unloaded package`() {
            // Let's make sure the packages that are expected
            // to be unloaded are indeed unloaded.
            val nested2L = "unloaded.nested1.nested2" // Use literals to prevent their loading.
            val nested3L = "$nested2L.nested3"
            loadedPackages.contains(nested2L).shouldBeFalse()
            loadedPackages.contains(nested3L).shouldBeFalse()

            // Call to the lookup should trigger loading of
            // the parental package with annotations.
            val nested4 = UnloadedNested4::class // `nested4` is NOT annotated.
            val annotation = lookup.getFor(nested4.java.`package`)
            loadedPackages.traversedTimes shouldBe 1

            // `nested2` has been loaded because it is annotated.
            // `nested3` has NOT been loaded because it has no annotations.
            loadedPackages.contains(nested2L).shouldBeTrue()
            loadedPackages.contains(nested3L).shouldBeFalse()

            val nested2 = UnloadedNested2::class
            annotation.shouldNotBeNull()
            annotation.anchor shouldBe nested2

            // Though, they both have been cached by the lookup. And requesting
            // annotation for `nested2` or `nested3` doesn't trigger an actual search.
            val annotation2 = lookup.getFor(UnloadedNested2::class.java.`package`)
            val annotation3 = lookup.getFor(UnloadedNested3::class.java.`package`)
            loadedPackages.traversedTimes shouldBe 1

            annotation2.shouldNotBeNull()
            annotation2.anchor shouldBe nested2
            annotation3.shouldNotBeNull()
            annotation3.anchor shouldBe nested2
        }
    }

    @Nested inner class
    `cache midway parental package` {

        @Test
        fun `without annotation`() {
            val nested4 = Nested4::class
            val nested3 = Nested3::class

            val annotations = listOf(
                lookup.getFor(nested4.java.`package`), // First call caches parents.
                lookup.getFor(nested3.java.`package`),
                lookup.getFor(nested3.java.`package`)
            )

            loadedPackages.traversedTimes shouldBe 1
            val nested2 = Nested2::class
            annotations.shouldForAll {
                it.shouldNotBeNull()
                it.anchor shouldBe nested2
            }
        }

        @Test
        fun `with annotation`() {
            val nested4 = Nested4::class
            val nested2 = Nested2::class

            val annotations = listOf(
                lookup.getFor(nested4.java.`package`), // First call caches parents.
                lookup.getFor(nested2.java.`package`),
                lookup.getFor(nested2.java.`package`)
            )

            loadedPackages.traversedTimes shouldBe 1
            annotations.shouldForAll {
                it.shouldNotBeNull()
                it.anchor shouldBe nested2
            }
        }
    }

    @Test
    fun `return 'null' if the package itself and its parents are not annotated`() {
        val nested1 = Nested1::class
        val annotation = lookup.getFor(nested1.java.`package`)
        annotation.shouldBeNull()
    }

    @Test
    fun `avoid loading of higher-level packages when the annotated one is already found`() {

        // `nested1` is not annotated itself.
        // And its parental packages are not annotated.
        lookup.getFor(Nested1::class.java.`package`).shouldBeNull()
        packageLoadings.askedLoadings["io"] shouldBe 1
        packageLoadings.askedLoadings["io.spine"] shouldBe 1

        // `nested3` is not annotated itself.
        // But its direct parent is. We should not go up to `io` and `io.spine` at all.
        lookup.getFor(Nested3::class.java.`package`).shouldNotBeNull()
        packageLoadings.askedLoadings["io"] shouldBe 1
        packageLoadings.askedLoadings["io.spine"] shouldBe 1

        // `nested4` is not annotated itself.
        // But its direct parent has already inherited the annotation and has been cached.
        // We should not go up to `io` and `io.spine` at all.
        lookup.getFor(Nested4::class.java.`package`).shouldNotBeNull()
        packageLoadings.askedLoadings["io"] shouldBe 1
        packageLoadings.askedLoadings["io.spine"] shouldBe 1
    }
}

private class MemoizingPackagesProvider {

    var traversedTimes = 0
        private set

    fun get(): Iterable<Package> {
        traversedTimes++
        return Package.getPackages().asIterable()
    }

    fun contains(packageSuffix: String) =
        Package.getPackages().any { it.name.endsWith(packageSuffix) }
}

private class MemoizingPackageLoader {

    private val _askedLoadings = mutableMapOf<PackageName, Int>()
    val askedLoadings: Map<PackageName, Int> = _askedLoadings

    fun load(packageName: PackageName): Package? {
        val result = DefaultPackageLoader.load(packageName)
        _askedLoadings[packageName] = (askedLoadings[packageName] ?: 0) + 1
        return result
    }
}
