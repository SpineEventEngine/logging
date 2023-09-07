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
 * searched packages don't need an actual search.
 *
 * ## Caching
 *
 * When the lookup inspects parental packages, they are also all cached.
 * Since we have already fetched the loaded parental packages (or tried
 * to force-load them), there's no reason not to cache them too.
 *
 * Traversing and caching of parental packages will continue even if we have
 * already found the closest annotated parent. Checking two-three-five more
 * packages is not costly when instances of [Package] are already at hand.
 * Otherwise, it may cause many unnecessary repeated force-loadings.
 */
internal class PackageAnnotationLookup<T : Annotation>(

    /**
     * The type of annotations this lookup will be looking for.
     *
     * There are two requirements for the passed annotation:
     *
     * 1. It should NOT be repeatable. As for now, lookup for repeatable
     * annotations is not supported.
     * 2. It should be applicable to packages. Otherwise, lookup is useless.
     */
    private val wantedAnnotation: Class<T>,

    /**
     * A tool for working with [packages][Package].
     *
     * The requested interface already provides default implementation
     * for all methods. And this class doesn't need more.
     *
     * The ability to pass another implementation is preserved for tests.
     * This class is performance-sensitive, so tests should also assert
     * whether it uses cached data whenever it is possible.
     */
    private val jvmPackages: JvmPackages = object : JvmPackages { }
) {

    /**
     * Packages for which presence of [T] annotation is already known.
     *
     * This map contains both directly annotated packages and ones
     * that have any parent annotated with [T] (propagated annotation).
     */
    private val knownPackages = hashMapOf<PackageName, T?>()

    init {
        val annotations = wantedAnnotation.annotations
        require(annotations.all { it.annotationClass != Repeatable::class }) {
            "Lookup for repeatable annotations is not supported."
        }
        val target = annotations.firstOrNull { it.annotationClass == Target::class } as Target?
        require(target == null || target.value.contains(ElementType.PACKAGE)) {
            "The configured annotation should be applicable to packages."
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
     * The method returns `null`.
     */
    fun getFor(pkg: Package): T? {
        val packageName = pkg.name
        val isKnown = knownPackages.contains(packageName)

        if (!isKnown) {
            val annotation = pkg.getAnnotation(wantedAnnotation)
            if (annotation != null) {
                knownPackages[packageName] = annotation
            } else {
                val inspectedPackages = searchWithinHierarchy(packageName)
                inspectedPackages.forEach { (name, annotation) ->
                    knownPackages[name] = annotation
                }
            }
        }

        return knownPackages[packageName]
    }

    /**
     * Iterates from the given [packageName] down to a root package,
     * looking for applied annotations of type [T].
     *
     * Also, this method propagates the found annotations from parental
     * packages to nested ones if they are not annotated themselves.
     * Take a look on example in docs to [propagateAnnotations] method.
     */
    private fun searchWithinHierarchy(packageName: PackageName): Map<PackageName, T?> {
        val possibleHierarchy = jvmPackages.expand(packageName) // package + its POSSIBLE parents.
        val loadedHierarchy = loadedHierarchy(packageName) // package + its LOADED parents.
        val withAnnotations = findAnnotations(possibleHierarchy, loadedHierarchy)
        val withPropagation = propagateAnnotations(withAnnotations)
        return withPropagation
    }

    /**
     * Fetches already loaded packages that relate to the given [packageName].
     *
     * It includes all loaded parental packages of [packageName] and the asked
     * package itself.
     */
    private fun loadedHierarchy(packageName: PackageName): Map<PackageName, Package> =
        jvmPackages.alreadyLoaded()
            .filter { packageName.startsWith(it.name) }
            .associateBy { it.name }

    /**
     * Associates an instance of [wantedAnnotation] with each package
     * from [possiblePackages], if a package has one directly applied.
     *
     * Otherwise, associates `null`.
     *
     * The returned map preserves iteration order of [possiblePackages].
     *
     * We can know whether a package is annoted only if it is loaded.
     * So, firstly, this method checks if a package is already loaded.
     * If not, it tries to force its loading.
     *
     * A failed loading indicates one of the following:
     *
     * 1. The package doesn't exist at all.
     * 2. It doesn't have any runtime-retained annotation.
     *
     * For this method, it doesn't matter why exactly it can't be force-loaded.
     * Both cases are counted as "the package is not annotated".
     *
     * Please note, this method would stop traversing through [possiblePackages]
     * when it meets an already known package. It means all upcoming packages
     * are also already known. In this case, the returned map will not contain
     * all packages from [possiblePackages] collection, and doesn't need to.
     */
    private fun findAnnotations(
        possiblePackages: List<PackageName>,
        loadedPackages: Map<PackageName, Package>
    ): Map<PackageName, T?> {
        val result = linkedMapOf<PackageName, T?>()
        for (name in possiblePackages) {
            if (knownPackages.contains(name)) {
                result[name] = knownPackages[name] // It will be used for the propagation.
                break // All further packages are already known.
            }
            val loadedPackage = loadedPackages[name] ?: jvmPackages.tryLoading(name)
            val foundAnnotation = loadedPackage?.getAnnotation(wantedAnnotation)
            result[name] = foundAnnotation
        }
        return result
    }

    /**
     * Propagates found annotations to child packages that don't have
     * their own annotations.
     *
     * For example, consider the following package: `P1.P2.P3.P4.P5.P6`.
     * Let's say `P1` and `P4` are annotated with `A1` and `A4`.
     * Without propagation, we would get the following map:
     *
     * ```
     * P1 to A1
     * P2 to null
     * P3 to null
     * P4 to A4
     * P5 to null
     * P6 to null
     * ```
     *
     * With propagation, the following result will be returned:
     *
     * ```
     * P1 to A1
     * P2 to A1
     * P3 to A1
     * P4 to A4
     * P5 to A4
     * P6 to A4
     * ```
     */
    private fun propagateAnnotations(withAnnotations: Map<PackageName, T?>): Map<PackageName, T?> {
        var lastFound: T? = null
        val propagated = withAnnotations.entries
            .reversed()
            .associate { (name, annotation) ->
                lastFound = annotation ?: lastFound
                name to lastFound
            }
        return propagated
    }
}
