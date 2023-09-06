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
     */
    private val annotationClass: Class<T>,

    /**
     * A tool for working with [packages][Package].
     *
     * [JvmPackages] already provides default implementation for all methods.
     * And this class doesn't need more.
     *
     * The ability to pass another implementation is preserved for tests.
     * This class is performance-sensitive, so tests should also assert
     * whether it uses cached whenever it is possible.
     */
    private val jvmPackages: JvmPackages = object : JvmPackages { }
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

        val directAnnotation = pkg.getAnnotation(annotationClass)
        if (directAnnotation != null) {
            knownPackages[packageName] = directAnnotation
        } else {
            val searchResult = searchWithinHierarchy(packageName)
            searchResult.forEach { (name, annotation) ->
                knownPackages[name] = annotation
            }
        }

        return knownPackages[packageName]
    }

    private fun searchWithinHierarchy(packageName: PackageName): Map<PackageName, T?> {
        val expectedHierarchy = jvmPackages.expand(packageName)
        expectedHierarchy.forEach(::println)
        println()

        val alreadyLoadedPackages by lazy { alreadyLoaded(packageName) }

        val inspectedPackages = mutableMapOf<PackageName, T?>()

        var lastFoundAnnotation: T? = null
        for (name in expectedHierarchy) {
            val isAlreadyKnown = knownPackages.contains(name)
            if (isAlreadyKnown) {
                val annotation = knownPackages[name]
                if (annotation != null) {
                    lastFoundAnnotation = annotation
                }
                inspectedPackages[name] = lastFoundAnnotation
                continue
            }

            val alreadyLoaded = alreadyLoadedPackages[name]
            if (alreadyLoaded != null) {
                val annotation = alreadyLoaded.getAnnotation(annotationClass)
                if (annotation != null) {
                    lastFoundAnnotation = annotation
                }
                inspectedPackages[name] = lastFoundAnnotation
                continue
            }

            val forceLoaded = jvmPackages.tryLoading(name)
            if (forceLoaded != null) {
                val annotation = forceLoaded.getAnnotation(annotationClass)
                if (annotation != null) {
                    lastFoundAnnotation = annotation
                }
                inspectedPackages[name] = lastFoundAnnotation
                continue
            }

            // Here we don't care much whether a package `name` exists or not.
            // If it doesn't – it wouldn't ever be asked. If it would – will inherit
            // annotation from the closest parent.
            knownPackages[name] = lastFoundAnnotation
        }

        inspectedPackages.entries.forEach(::println)
        println()
        return inspectedPackages
    }

    /**
     * Fetches already loaded packages that relate to the given [packageName].
     *
     * It includes all loaded parental packages of [packageName] as well
     * as the asked package itself.
     *
     * Although the lookup already has an instance of [Package] for [packageName],
     * its repeated fetching along with parents is cheaper than filtering out.
     */
    private fun alreadyLoaded(packageName: PackageName): Map<PackageName, Package> =
        jvmPackages.alreadyLoaded()
            .filter { packageName.startsWith(it.name) }
            .associateBy { it.name }
}
