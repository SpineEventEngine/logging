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

import java.lang.annotation.ElementType
import java.lang.annotation.Repeatable
import java.lang.annotation.Target

private typealias PackageName = String

/**
 * Locates an annotation of type [T] for the asked package, if any.
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
 * searched packages don't need an actual search.
 */
public class PackageAnnotationLookup<T : Annotation>(

    /**
     * Type of annotations this lookup can locate.
     */
    private val annotationClass: Class<T>,

    /**
     * Provider of the currently loaded packages.
     *
     * The default supplier is [Package.getPackages].
     */
    private val currentlyLoadedPackages: () -> Iterable<Package> =
        { Package.getPackages().asIterable() },

    private val packageLoader: (PackageName) -> Package? =
        { DefaultPackageLoader.load(it) }
) {

    /**
     * Packages for which presence of [T] annotation is already known.
     *
     * Hash map is fast when retrieving values by string key.
     * So, annotations are mapped to [Package.name] instead of [Package].
     */
    private val knownPackages = hashMapOf<PackageName, T?>()

    init {
        val annotations = annotationClass.annotations

        require(annotations.all { it.annotationClass != Repeatable::class }) {
            "The configured annotation should NOT be repeatable."
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
     * or any of its parental packages.
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
     * Please note, it can happen that different class loaders may load the same
     * package several times. See docs to [Package.getPackages] for details.
     * If, for example, two [Package]s with the same name are both annotated with [T],
     * the method will just return the first found one.
     */
    public fun getFor(pkg: Package): T? {
        val packageName = pkg.name
        val isPackageUnknown = knownPackages.contains(packageName).not()

        if (isPackageUnknown) {
            val annotation = pkg.getAnnotation(annotationClass)
            if (annotation != null) {
                // The simplest case is when the package itself is annotated.
                knownPackages[packageName] = annotation
            } else {
                // Otherwise, the method has to search it within the parental packages.
                val searchResult = searchWithinParents(packageName)
                val packagesToUpdate = searchResult.checkedParents + packageName
                updateKnownPackages(packagesToUpdate, searchResult.foundAnnotation)
            }
        }

        return knownPackages[packageName]
    }

    /**
     * Searches for the nearest parent of the [pkg]
     * that is annotated with [T].
     *
     * Returns all parental packages that have been checked for presence of [T],
     * starting from the direct parent of [pkg] down to the one,
     * that is annotated with [T].
     *
     * If no parent has [T] applied, [SearchResult.foundAnnotation] will contain `null`.
     * And [SearchResult.checkedParents] will contain all parents of the [pkg].
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
     * Iterates through the all currently loaded packages
     * to find parents of the [pkg].
     */
    private fun parentalPackages(pkg: PackageName): Map<PackageName, Package?> {
        println("Fetching parents of $pkg.")
        val alreadyLoadedParents = currentlyLoadedPackages()
            .filter { pkg.startsWith(it.name) }
            .filter { it.name != pkg }
            .sortedByDescending { it.name.length }
        val expectedParents = expectedParents(pkg)
        val currentlyLoadedParents = loadAbsentParents(expectedParents, alreadyLoadedParents)
        return currentlyLoadedParents
    }

    private fun loadAbsentParents(
        expectedParents: List<PackageName>,
        alreadyLoadedParents: List<Package>
    ): Map<PackageName, Package?> {
        return expectedParents.mapIndexed { i, name ->
            val loaded = alreadyLoadedParents.getOrNull(i)
            if (loaded != null && loaded.name == name) {
                name to loaded
            } else {
                val loadedPackage = packageLoader(name)
                name to loadedPackage
            }
        }.toMap() // The returned map preserves the iteration order.
    }

    private fun expectedParents(pkg: PackageName): List<PackageName> {
        val allParents = pkg.mapIndexed { index, c ->
            if (c == '.') {
                pkg.substring(0, index)
            } else {
                null
            }
        }
        val sorted = allParents.filterNotNull().sortedByDescending { it.length }
        return sorted
    }

    private fun updateKnownPackages(packages: List<PackageName>, annotation: T?) =
        packages.forEach { knownPackages[it] = annotation }

    private class SearchResult<T : Annotation>(
        val checkedParents: List<PackageName>,
        val foundAnnotation: T?
    )

    private class DefaultPackageLoader {
        companion object {
            fun load(name: PackageName): Package? {
                val packageInfoClassName = "$name.package-info"
                val packageInfoClass: Class<*>? =
                    try {
                        Class.forName(packageInfoClassName)
                    } catch (_: ClassNotFoundException) {
                        null
                    }
                return packageInfoClass?.`package`
            }
        }
    }
}
