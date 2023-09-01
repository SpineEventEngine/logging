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
 * Locates annotations of type [T] for the requested packages.
 *
 * This lookup is similar to [AnnotatedPackages][io.spine.reflect.AnnotatedPackages].
 *
 * [AnnotatedPackages][io.spine.reflect.AnnotatedPackages] eagerly traverses
 * all loaded packages during the instance creation. It looks for ones
 * that are annotated with the given annotation type, remembers them,
 * and then allows retrieving of an annotation for the requested package.
 *
 * But as more classes are loaded by the classloader, more new packages appear.
 * As a result, data within the collection become insufficient. An instance
 * doesn't know about every currently loaded package.
 *
 * This implementation performs searching on demand, and remembers
 * the already visited packages to optimize consequent requests.
 * It works because presence of [Package] instance guarantees
 * that the package is already loaded.
 */
public class AnnotationsLookup<T : Annotation>(

    /**
     * Type of annotations this lookup can locate.
     */
    private val annotationClass: KClass<T>,
) {

    /**
     * Hash map is quite fast when retrieving values by string key.
     */
    private val knownPackages = hashMapOf<PackageName, T?>()

    /**
     * Returns annotation of type [T] that is applied to the given
     * [requestedPackage] or any of its parental packages.
     *
     * This method considers three general cases:
     *
     * 1. The given package itself is annotated with [T].
     * The method returns that annotation.
     * 2. The given package is NOT annotated, but one of the parental packages is.
     * The method returns annotation of the closest annotated parent.
     * 3. Neither the given package nor any of its parental packages is annotated.
     * The method will return `null`.
     *
     * Besides the general cases, there is a special: different class loaders
     * (within the same hierarchy) may load the same package several times.
     * They put several [Package] instances with the same [Package.name].
     * Although such is not expected in a typical flow, the method will
     * report conflicting annotations. It throws [IllegalStateException]
     * when two or more [Package] instances with the same name are annotated
     * with [T]. In this case, the method can't surely say whose [T]
     * should be returned.
     */
    public fun getFor(requestedPackage: Package): T? {
        val packageName = requestedPackage.name

        // Map values are nullable, so check the key presence explicitly.
        val isAlreadyKnown = knownPackages.contains(packageName)
        if (isAlreadyKnown) {
            return knownPackages[packageName]
        }

        val scannedPackages = scan(requestedPackage)
        updateKnownPackages(scannedPackages)

        return knownPackages[packageName]
    }

    private fun scan(requestedPackage: Package): Collection<Pair<PackageName, T?>> {

        val packageName = requestedPackage.name

        // Case 1: the package itself IS annotated.
        // Return only the requested package along with the applied annotation.

        // Case 2: the package itself is NOT annotated, but it may have an annotated parent.
        // Let's go up the package hierarchy to find the closest annotated package.

        // P.S. Cases 1 AND 2 have been merged. As implementation of case 2 also covers case 1.

        // Packages are loaded along with classes.
        // The number of loaded packages may increase as the program runs.
        val allLoadedPackages = Package.getPackages()

        // Find all parental packages along with the requested one.
        // The closest ones go first.
        val parentalPackages = allLoadedPackages.filter { packageName.startsWith(it.name) }
            .sortedByDescending { it.name.length }

        // We will stop traversing upon the first occurrence of the wanted annotation.
        // If found, all already traversed packages will inherit the found annotation.
        val alreadyVisited = mutableListOf<PackageName>()
        var foundAnnotation: T? = null
        for (parentalPackage in parentalPackages) {
            @Suppress("UNCHECKED_CAST") // Cast to `annotationClass` type is safe.
            val appliedAnnotation = parentalPackage.annotations
                .firstOrNull { annotationClass.isInstance(it) } as T?
            val name = parentalPackage.name
            alreadyVisited.add(name)

            if (appliedAnnotation != null) {
                foundAnnotation = appliedAnnotation
                break
            }
        }

        // If found, all traversed will get the found one. Otherwise, they will get `null`.
        val traversedPackages = alreadyVisited.map { it to foundAnnotation }

        // Covers a case, when the same package is loaded from different classloaders,
        // and they both are annotated. Whose annotation should be returned?

        // In general, it is a possible case, which we don't support explicitly.
        // If there are several packages with the same name, and only one of them
        // is annotated – that annotation would be returned.

        // But if there are two or more same-named packages are annotated, we would throw.
        // It is unclear whose annotation should be returned.

        val conflictingDuplicates = traversedPackages.groupBy({ it.first }, { it.second })
            .filter { it.value.filterNotNull().size > 1 }
            .filter { it.value.size > it.value.distinct().size }
        check(conflictingDuplicates.isEmpty()) {
            "The same package is loaded several times from different classloaders, " +
                    "and two or more of them are annotated with `@${annotationClass.simpleName}`."
        }

        // At this stage we have done out best.
        return traversedPackages
    }

    private fun updateKnownPackages(traversedPackages: Collection<Pair<PackageName, T?>>) =
        traversedPackages.forEach { knownPackages[it.first] = it.second }
}
