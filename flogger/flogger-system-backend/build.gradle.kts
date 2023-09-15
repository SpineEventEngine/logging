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

import io.spine.internal.dependency.AutoService
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `jvm-module`

    /**
     * Although, Kapt is being replaced with KSP now, the official implementation
     * of AutoService for KSP is not available yet.
     *
     * See [KSP Implementation of AutoService](https://github.com/google/auto/issues/882).
     */
    `kotlin-kapt`
}

dependencies {
    implementation(project(":flogger-api"))
    testImplementation(project(":flogger-testing"))
    testImplementation(AutoService.annotations)
    kaptTest(AutoService.processor)
}


java {

    /**
     * Disables Java linters until main sources are migrated to Kotlin.
     *
     * As for now, they produce a lot of errors/warnings to original Flogger code,
     * failing the build.
     */
    tasks {
        named("checkstyleMain") { enabled = false }
        named("pmdMain") { enabled = false }
        compileJava { options.errorprone.isEnabled.set(false) }
    }
}
