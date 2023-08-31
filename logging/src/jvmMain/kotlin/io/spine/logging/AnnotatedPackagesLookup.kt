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

public class AnnotatedPackagesLookup<T : Annotation>(
    private val annotationClass: KClass<T>,
) {

    private val knownPackages = hashMapOf<PackageName, T?>()

    /**
     * `java.lang.Package` doesn't have a counterpart in Kotlin.
     */
    public fun search(requestedPackage: Package): T? {
        val packageName = requestedPackage.name

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

/**
 * During the sorting, parent packages go first.
 */
public fun main() {
    val packages = listOf(
        "io.spine.logging.domain",
        "io.spine.logging.adapter",
        "io.spine.logging",
        "io.spine.logging.context.filter",
        "io.spine"
    )
    println(packages.sorted())
    /*
    io.spine,
    io.spine.logging,
    io.spine.logging.adapter,
    io.spine.logging.context.filter,
    io.spine.logging.domain
     */
}

// For example: io.spine.logging.domain

// Case 1: `io` is annotated.
// io, io.spine, io.spine.logging, io.spine.logging.domain

// Case 2: `io.spine` is annotated.
// io.spine, io.spine.logging, io.spine.logging.domain

// Case 3: `io.spine.logging` is annotated.
// io.spine.logging, io.spine.logging.domain

// Case 4: `io.spine.logging.domain` is annotated.
// io.spine.logging.domain
