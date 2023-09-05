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

/**
 * Java package loading mechanism based on loading of JVM-internal `package-info` class.
 *
 * `package-info.java` is usually placed along the package itself. It is used
 * to document and annotate the package. Internally, in runtime, it is represented
 * as a class with a special name.
 *
 * Loading of this class triggers loading of the containing package.
 * But such a class is present only if the package has at least one annotation.
 * Otherwise, `package-info.java` will not have the corresponding internal class.
 * It is because no information is needed to be bypassed to the runtime.
 */
internal class PackageInfoPackageLoader : JavaPackageLoader {

    /**
     * Tries to load a package with the given [name].
     *
     * Returns the loaded package if it is on classpath and has at least
     * one runtime-available annotation. Otherwise, returns `null`.
     */
    override fun tryLoading(name: PackageName): Package? {
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
