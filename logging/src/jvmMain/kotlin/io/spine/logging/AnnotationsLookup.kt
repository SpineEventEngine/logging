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

import kotlin.reflect.KClass

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
public class AnnotationsLookup<T : Annotation>(

    /**
     * Type of annotations this lookup can locate.
     */
    private val annotationClass: KClass<T>,

    /**
     * Provider of the currently loaded packages.
     *
     * The default supplier is [Package.getPackages].
     */
    private val currentlyLoadedPackages: () -> Iterable<Package> =
        { Package.getPackages().asIterable() }
) {

    /**
     * Packages for which presence of [T] annotation is already known.
     *
     * Hash map is fast when retrieving values by string key.
     * So, annotations are mapped to [Package.name] instead of [Package].
     */
    private val knownPackages = hashMapOf<PackageName, T?>()

    /**
     * Returns annotation of type [T] that is applied to the given [askedPackage],
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
    public fun getFor(askedPackage: Package): T? {
        val packageName = askedPackage.name
        val isPackageUnknown = knownPackages.contains(packageName).not()

        if (isPackageUnknown) {
            val annotation = askedPackage.findAnnotation(annotationClass)
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
     * Searches for the nearest parent of the [askedPackage]
     * that is annotated with [T].
     *
     * Returns all parental packages that have been checked for presence of [T],
     * starting from the direct parent of [askedPackage] down to the one,
     * that is annotated with [T].
     *
     * If no parent has [T] applied, [SearchResult.foundAnnotation] will contain `null`.
     * And [SearchResult.checkedParents] will contain all parents of the [askedPackage].
     */
    private fun searchWithinParents(askedPackage: PackageName): SearchResult<T> {
        val parentalPackages = parentalPackages(askedPackage)
        val checkedParents = mutableListOf<PackageName>()
        var foundAnnotation: T? = null

        for (parentPackage in parentalPackages) {
            val annotation = parentPackage.findAnnotation(annotationClass)
            checkedParents.add(parentPackage.name)
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
     * to find parents of the [askedPackage].
     */
    private fun parentalPackages(askedPackage: PackageName): List<Package> {
        val parentalPackages = currentlyLoadedPackages()
            .filter { askedPackage.startsWith(it.name) }
            .filter { it.name != askedPackage }
            .sortedByDescending { it.name.length }
        return parentalPackages
    }

    private fun updateKnownPackages(packages: List<PackageName>, annotation: T?) =
        packages.forEach { knownPackages[it] = annotation }

    private class SearchResult<T : Annotation>(
        val checkedParents: List<PackageName>,
        val foundAnnotation: T?
    )
}

private fun <T: Annotation> Package.findAnnotation(annotationClass: Class<in T>): T? {
    @Suppress("UNCHECKED_CAST") // Cast to `annotationClass` is safe.
    return annotations.firstOrNull { annotationClass.isInstance(it) } as T?
}
