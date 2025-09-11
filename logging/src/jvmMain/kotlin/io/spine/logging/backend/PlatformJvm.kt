/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.logging.backend

internal actual fun loadPlatform(): Platform {

    /**
     * Non-final to prevent javac inlining.
     */
    @Suppress("ConstantField")
    val defaultPlatform = "io.spine.logging.backend.system.DefaultPlatform"

    /**
     * The first available platform from this list is used.
     *
     * Each platform is defined separately outside of this array
     * so that the `IdentifierNameString` annotation can be applied to each.
     * This annotation tells Proguard that these strings refer to class names.
     * If Proguard decides to obfuscate those classes, it will also obfuscate
     * these strings so that reflection can still be used.
     */
    val availablePlatforms: Array<String> = arrayOf(
        // The fallback/default platform gives a workable, logging backend.
        defaultPlatform
    )

    val platform: Platform = loadFirstAvailablePlatform(availablePlatforms)
    return platform
}

@Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException")
private fun loadFirstAvailablePlatform(platformClass: Array<String>): Platform {
    val errorMessage = StringBuilder()
    // Try the reflection-based approach as a backup, if the provider isn't available.
    for (clazz in platformClass) {
        try {
            return Class.forName(clazz)
                .getConstructor()
                .newInstance() as Platform
        } catch (e: Throwable) {
            // Catch errors so if we can't find _any_ implementations,
            // we can report something useful.
            // Unwrap any generic wrapper exceptions for readability here
            // (extend this as needed).
            val th = if (e::class.simpleName!!.contains("InvocationTargetException")) {
                e.cause
            } else {
                e
            }
            errorMessage.append('\n')
                .append(clazz)
                .append(": ")
                .append(th)
        }
    }
    error(errorMessage.insert(0, "No logging platforms found:").toString())
}
