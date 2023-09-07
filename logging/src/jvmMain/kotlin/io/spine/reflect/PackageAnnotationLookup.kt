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
 * searched packages don't need an actual search. The inspected midway parental
 * packages are also cached.
 */
internal class PackageAnnotationLookup<T : Annotation>(

    /**
     * The type of annotations this lookup will be looking for.
     *
     * There are two requirements for the passed annotation:
     *
     * 1. It should NOT be repeatable. As for now, lookup for repeatable
     * annotations is not supported.
     * 2. It should be applicable to packages. Otherwise, the lookup is useless.
     */
    private val wantedAnnotation: Class<T>,

    /**
     * A tool for working with [packages][Package].
     *
     * [JvmPackages] already provides default implementation for all methods.
     * And this class doesn't need more.
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
     *
     * Hash map is fast when retrieving values by string key.
     * So, annotations are mapped to [Package.name] instead of [Package].
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
     * The method will return `null`.
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
     * Iterates from a root package down to the given one,
     * looking for applied annotations of type [T].
     *
     * This method would propagate a found annotation to child packages,
     * which don't have their own.
     *
     * For example, consider the following package: `p1.p2.p3.p4.p5.p6`.
     * Let's say `p1` and `p4` are annotated with `t1` and `t4`.
     * Without propagation, we would get the following map:
     *
     * ```
     * p1 to t1
     * p2 to null
     * p3 to null
     * p4 to t4
     * p5 to null
     * p6 to null
     * ```
     *
     * With propagation, the following result will be returned:
     *
     * ```
     * p1 to t1
     * p2 to t1
     * p3 to t1
     * p4 to t4
     * p5 to t4
     * p6 to t4
     * ```
     */
    private fun searchWithinHierarchy(packageName: PackageName): Map<PackageName, T?> {
        val possibleHierarchy = jvmPackages.expand(packageName) // package + its POSSIBLE parents.
        val loadedHierarchy = loadedHierarchy(packageName) // package + its LOADED parents.
        val withAnnotations = annotate(possibleHierarchy, loadedHierarchy)
        val withPropagation = propagate(withAnnotations)
        return withPropagation
    }

    private fun propagate(withAnnotations: Map<PackageName, T?>): Map<PackageName, T?> {
        var lastFound: T? = null
        val propagated = withAnnotations.entries
            .reversed()
            .associate { (name, annotation) ->
                if (annotation != null) {
                    lastFound = annotation
                    name to annotation
                } else {
                    name to lastFound
                }
            }
        return propagated
    }

    private fun annotate(
        possiblePackages: List<PackageName>,
        loadedPackages: Map<PackageName, Package>
    ): Map<PackageName, T?> {
        val result = mutableMapOf<PackageName, T?>()
        for (name in possiblePackages) {
            if (knownPackages.contains(name)) {
                val knownAnnotation = knownPackages[name]
                result[name] = knownAnnotation
                break
            }
            val foundAnnotation = findAnnotation(name, loadedPackages)
            result[name] = foundAnnotation
        }
        return result
    }

    /**
     * Fetches already loaded packages that relate to the given [packageName].
     *
     * It includes all parental packages of [packageName] and the asked
     * package itself.
     */
    private fun loadedHierarchy(packageName: PackageName): Map<PackageName, Package> =
        jvmPackages.alreadyLoaded()
            .filter { packageName.startsWith(it.name) }
            .associateBy { it.name }

    private fun findAnnotation(name: PackageName, loadedPackages: Map<PackageName, Package>): T? {
        val fromAlreadyLoaded = loadedPackages[name]?.findAnnotation()
        if (fromAlreadyLoaded != null) {
            return fromAlreadyLoaded
        }
        val fromForcedLoaded = jvmPackages.tryLoading(name)?.findAnnotation()
        return fromForcedLoaded
    }

    /**
     * Returns annotation of type [T] applied to this [Package], if any.
     */
    private fun Package.findAnnotation(): T? = getAnnotation(wantedAnnotation)
}
