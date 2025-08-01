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

@file:Suppress("unused") // source set accessed via `by getting`.

import io.spine.dependency.kotlinx.DateTime
import io.spine.dependency.local.Base
import io.spine.dependency.local.Reflect
import io.spine.gradle.publish.SpinePublishing
import io.spine.gradle.publish.spinePublishing
import org.gradle.kotlin.dsl.project

plugins {
    `kmp-module`
    `kmp-publish`
}

// This module configures `spinePublishing` on its own to change a prefix
// specified by the root project.
spinePublishing {
    destinations = rootProject.the<SpinePublishing>().destinations
    customPublishing = true
    dokkaJar {
        java = false
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(Reflect.lib)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(DateTime.lib)
                implementation(Base.annotations)
                implementation(Reflect.lib)
                implementation(
                    project(
                        ":platform-generator",
                        configuration = "generatedPlatformProvider"
                    )
                )
                runtimeOnly(project(":jvm-default-platform"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(project(":probe-backend"))
                implementation(project(":logging-testlib"))
            }
        }
    }
}
