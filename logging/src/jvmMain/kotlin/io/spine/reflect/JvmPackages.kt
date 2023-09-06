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

import java.lang.StringBuilder

/**
 * Name of a Java or Kotlin package.
 *
 * For example, `io.spine.reflect`.
 */
internal typealias PackageName = String

/**
 * A utility for working with [packages][Package].
 */
internal interface JvmPackages {

    /**
     * Returns packages that have already been loaded by the caller's
     * class loader and its ancestors.
     *
     * Packages are loaded along with classes they contain. When a class
     * loader loads a class from an unloaded package, it also loads
     * the containing package.
     *
     * During the runtime, the number of the loaded packages usually increases
     * as more classes become loaded. It decreases only if the caller's class
     * loader (or any of its ancestors) is garbage collected.
     */
    fun alreadyLoaded(): Iterable<Package> = Package.getPackages().asIterable()

    /**
     * Tries to load a package with the given [name].
     *
     * Returns the loaded package if it exists AND has at least one
     * runtime-retained annotation.
     *
     * Otherwise, returns `null`.
     *
     * JDK doesn't provide a straight way to force a package loading as,
     * for example, it provides for [classes][ClassLoader.loadClass].
     *
     * This method loads a JVM-internal `package-info` class to trigger
     * loading of the containing package. Such a class is present only if
     * the package has at least one runtime-retained annotation.
     *
     * Otherwise, `package-info.java` will not have the corresponding class,
     * as no information is needed to be bypassed to the runtime.
     */
    fun tryLoading(name: PackageName): Package? {
        val packageInfoClassName = "$name.package-info"
        val packageInfoClass: Class<*>? =
            try {
                Class.forName(packageInfoClassName)
            } catch (_: ClassNotFoundException) {
                null
            }
        return packageInfoClass?.`package`
    }

    /**
     * Expands all packages from the package with the given [name].
     *
     * For example, for `io.spine.reflect` this method would
     * return the following:
     *
     * ```
     * io
     * io.spine
     * io.spine.reflect
     * ```
     *
     * Please note, this method just operates upon the given package name.
     * Its result is not guaranteed to match an existent hierarchy of packages.
     */
    fun expand(name: PackageName): List<PackageName> {
        val buffer = StringBuilder(name.length)
        val expanded = mutableListOf<PackageName>()
        name.forEach { symbol ->
            if (symbol == '.') {
                expanded.add("$buffer")
            }
            buffer.append(symbol)
        }
        expanded.add(name)
        return expanded
    }
}
