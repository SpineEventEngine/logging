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

package io.spine.reflect

import java.lang.annotation.ElementType
import java.lang.annotation.Repeatable
import java.lang.annotation.Target

/**
 * Name of a Java package.
 *
 * Usually, it is a value from [Package.name].
 */
internal typealias PackageName = String

/**
 * Locates an annotation of type [T] for the asked package,
 * or for any of its parental packages.
 *
 * This lookup is similar to [AnnotatedPackages][io.spine.reflect.AnnotatedPackages].
 *
 * [AnnotatedPackages][io.spine.reflect.AnnotatedPackages] eagerly traverses
 * all loaded packages during the instance creation. It looks for ones
 * that are annotated with the given annotation type, remembers them,
 * and then allows retrieving of an annotation for the asked package.
 *
 * But as more classes are loaded by the classloader, more new packages appear.
 * As a result, data within the collection become insufficient. An instance
 * doesn't know about every currently loaded package.
 *
 * This implementation performs searching on demand with caching. It does
 * the actual search for packages that are asked for the first time.
 * The search result is remembered, so consequent requests for the previously
 * searched packages don't need an actual search. The inspected midway parental
 * packages are also cached.
 */
internal class PackageAnnotationLookup<T : Annotation>(

    /**
     * The type of annotations this lookup will be looking for.
     */
    private val annotationClass: Class<T>,

    /**
     * Provider of the currently loaded packages.
     *
     * The default provider is [Package.getPackages].
     */
    private val loadedPackages: () -> Iterable<Package> =
        { Package.getPackages().asIterable() },

    /**
     * Java package loading mechanism.
     *
     * The default one is based on loading of `package-info` class.
     * Take a look on [PackageInfoPackageLoader] for details.
     *
     * Anyway, the provided mechanism should be able to load
     * packages that have at least one runtime-available annotation.
     * And it doesn't matter whether it is of type [T] or not.
     */
    private val packageLoader: JavaPackageLoader = PackageInfoPackageLoader()
) {

    /**
     * Packages for which presence of [T] annotation is already known.
     *
     * This map contains both directly annotated packages and ones
     * that have any parent annotated with [T].
     *
     * Hash map is fast when retrieving values by string key.
     * So, annotations are mapped to [Package.name] instead of [Package].
     */
    private val knownPackages = hashMapOf<PackageName, T?>()

    init {
        val annotations = annotationClass.annotations

        require(annotations.all { it.annotationClass != Repeatable::class }) {
            "Lookup for repeatable annotations is not supported."
        }

        val target = annotations.firstOrNull { it.annotationClass == Target::class } as Target?
        if (target != null) { // Not present `@Target` allows applying to packages.
            require(target.value.contains(ElementType.PACKAGE)) {
                "The configured annotation should be applicable to packages."
            }
        }
    }

    /**
     * Returns annotation of type [T] that is applied to the given [pkg],
     * or to any of its parental packages.
     *
     * This method considers the following cases:
     *
     * 1. The given package itself is annotated with [T].
     * The method returns that annotation.
     * 2. The given package is NOT annotated, but one of the parental packages is.
     * The method returns annotation of the closest annotated parent.
     * 3. Neither the given package nor any of its parental packages is annotated.
     * The method will return `null`.
     *
     * Please note, it may happen that [loadedPackages] return several [Package]s
     * with the same name. Such is acceptable by the rules of JVM (see docs
     * to [Package.getPackages] for details). So, if, for example, two [Package]s
     * with the same name are both annotated with [T], the method will just return
     * the first found one.
     */
    fun getFor(pkg: Package): T? {
        val packageName = pkg.name
        val isAlreadyKnown = knownPackages.contains(packageName)

        if (isAlreadyKnown) {
            return knownPackages[packageName]
        }

        val annotation = pkg.getAnnotation(annotationClass)
        if (annotation != null) {
            knownPackages[packageName] = annotation
        } else {
            val searchResult = searchWithinParents(packageName)
            val inspectedPackages = searchResult.inspectedParents + packageName
            val maybeFoundAnnotation = searchResult.maybeFoundAnnotation
            updateKnownPackages(inspectedPackages, maybeFoundAnnotation)
        }

        return knownPackages[packageName]
    }

    /**
     * Searches for the nearest parent of the given [pkg]
     * that is annotated with [T].
     *
     * Returns all parental packages that have been checked for presence of [T],
     * starting from the direct parent of [pkg] up to the one,
     * that is annotated with [T].
     *
     * If no parent has [T] applied, [SearchResult.maybeFoundAnnotation]
     * will contain `null`. [SearchResult.inspectedParents] will contain
     * all parents of the [pkg] which could have this annotation but do not.
     *
     * For already loaded packages, this capability is checked by [Package.getAnnotation].
     * For not loaded packages, [packageLoader] is used. It should successfully
     * load a package if one has at least one annotation. So, it could be
     * the wanted annotation of type [T].
     */
    private fun searchWithinParents(pkg: PackageName): SearchResult<T> {
        val parentalPackages = parentalPackages(pkg)
        val checkedParents = mutableListOf<PackageName>()
        var foundAnnotation: T? = null

        for (parentPackage in parentalPackages) {
            val annotation = parentPackage.value?.getAnnotation(annotationClass)
            checkedParents.add(parentPackage.key)
            if (annotation != null) {
                foundAnnotation = annotation
                break
            }
        }

        val result = SearchResult(checkedParents, foundAnnotation)
        return result
    }

    /**
     * Iterates through the all parental packages of the given [pkg]
     * that MAY HAVE annotations.
     *
     * This method forces loading of parental packages that are not loaded yet,
     * but may be annotated with [T].
     *
     * Unfortunately, this method can't use [Sequence] because we have
     * to sort found parental packages down from the closest one.
     * Otherwise, [searchWithinParents] would have returned the annotation
     * for the first found parent instead of the closest one.
     *
     * Please see docs to [packageLoader].
     */
    private fun parentalPackages(pkg: PackageName): Map<PackageName, Package?> {
        val alreadyLoadedParents = loadedPackages()
            .filter { pkg.startsWith(it.name) }
            .filter { it.name != pkg }
            .sortedByDescending { it.name.length }
            .distinctBy { it.name }
        val allPossibleParents = allPossibleParents(pkg)
        val loadedParents = forceLoading(allPossibleParents, alreadyLoadedParents)
        return loadedParents
    }

    private fun forceLoading(
        expectedParents: List<PackageName>,
        alreadyLoadedParents: List<Package>
    ): Map<PackageName, Package?> {

        val loadedParents = mutableMapOf<PackageName, Package?>()
        var alreadyIndex = 0

        for (expectedIndex in expectedParents.indices) {
            val expectedName = expectedParents[expectedIndex]
            val alreadyLoaded = alreadyLoadedParents.getOrNull(alreadyIndex)
            if (alreadyLoaded != null && alreadyLoaded.name == expectedName) {
                loadedParents[expectedName] = alreadyLoaded
                alreadyIndex++
            } else {
                val forceLoaded = packageLoader.tryLoading(expectedName)
                loadedParents[expectedName] = forceLoaded
            }
        }

        // The returned map preserved the iteration order.
        return loadedParents
    }

    /**
     * Returns all possible parental packages for the package
     * with the given [name].
     */
    private fun allPossibleParents(name: PackageName): List<PackageName> {
        val allParents = name.mapIndexed { index, c ->
            if (c == '.') {
                name.substring(0, index)
            } else {
                null
            }
        }
        val sorted = allParents.filterNotNull()
            .sortedByDescending { it.length }
        return sorted
    }

    private fun updateKnownPackages(packages: List<PackageName>, annotation: T?) =
        packages.forEach { knownPackages[it] = annotation }

    private class SearchResult<T : Annotation>(
        val inspectedParents: List<PackageName>,
        val maybeFoundAnnotation: T?
    )
}
