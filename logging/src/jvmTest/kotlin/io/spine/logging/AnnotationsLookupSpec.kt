package io.spine.logging

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.reflect.given.LookupTestPackage
import io.spine.reflect.given.nested1.Nested1
import io.spine.reflect.given.nested1.nested2.Nested2
import io.spine.reflect.given.nested1.nested2.nested3.Nested3
import io.spine.reflect.given.nested1.nested2.nested3.nested4.Nested4
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * ```
 * nested1
 * → nested2 [annotated!]
 *    → nested3
 *        → nested4
 * ```
 */
@DisplayName("`AnnotationsLookup` should")
internal class AnnotationsLookupSpec {

    private val annotationClass = LookupTestPackage::class
    private val loadedPackages = MemoizingPackagesProvider()
    private val lookup = AnnotationsLookup(annotationClass, loadedPackages::get)

    @Nested inner class
    `return annotation if the asked package` {

        @Test
        fun `is directly annotated`() {
            val anchor = Nested2::class
            val askedPackage = anchor.java.`package`
            val annotation = lookup.getFor(askedPackage)
            annotation.shouldNotBeNull()
            annotation.anchor shouldBe anchor
        }

        @Test
        fun `is indirectly annotated`() {
            val anchor = Nested4::class // `nested4` is not annotated.
            val expectedAnchor = Nested2::class // It will get `nested2` annotation.
            val askedPackage = anchor.java.`package`
            val annotation = lookup.getFor(askedPackage)
            annotation.shouldNotBeNull()
            annotation.anchor shouldBe expectedAnchor
        }
    }

    @Test
    fun `return 'null' if the asked package and its parents are not annotated`() {
        val anchor = Nested1::class
        val askedPackage = anchor.java.`package`
        val annotation = lookup.getFor(askedPackage)
        annotation.shouldBeNull()
    }

    @Test
    fun `not scan loaded packages if the asked package is annotated`() {
        val anchor = Nested2::class
        val askedPackage = anchor.java.`package`
        val annotation1 = lookup.getFor(askedPackage)
        val annotation2 = lookup.getFor(askedPackage)
        annotation1.shouldNotBeNull()
        annotation1 shouldBeSameInstanceAs annotation2
        loadedPackages.traversedTimes shouldBe 0
    }

    @Test
    fun `cache midway parental package without annotation`() {
        val anchor = Nested4::class
        val midwayAnchor = Nested3::class
        val expectedAnchor = Nested2::class

        lookup.getFor(anchor.java.`package`) // Triggers caching of parents.
        val annotation1 = lookup.getFor(midwayAnchor.java.`package`)
        val annotation2 = lookup.getFor(midwayAnchor.java.`package`)

        annotation1.shouldNotBeNull()
        annotation1.anchor shouldBe expectedAnchor
        annotation1 shouldBeSameInstanceAs annotation2
        loadedPackages.traversedTimes shouldBe 1
    }

    @Test
    fun `cache midway parental package with annotation`() {
        val anchor = Nested4::class
        val midwayAnchor = Nested2::class

        lookup.getFor(anchor.java.`package`) // Triggers caching of parents.
        val annotation1 = lookup.getFor(midwayAnchor.java.`package`)
        val annotation2 = lookup.getFor(midwayAnchor.java.`package`)

        annotation1.shouldNotBeNull()
        annotation1.anchor shouldBe midwayAnchor
        annotation1 shouldBeSameInstanceAs annotation2
        loadedPackages.traversedTimes shouldBe 1
    }
}

private class MemoizingPackagesProvider {

    var traversedTimes = 0
        private set

    fun get(): Iterable<Package> {
        traversedTimes++
        return Package.getPackages().asIterable()
    }
}
