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

@file:Suppress("UNUSED_VARIABLE") // for source sets.

import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.JUnit
import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.SystemLambda
import io.spine.internal.gradle.javadoc.JavadocConfig
import io.spine.internal.gradle.publish.IncrementGuard
import io.spine.internal.gradle.testing.configureLogging
import io.spine.internal.gradle.testing.registerTestTasks
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.spine.internal.gradle.publish.javadocJar
import io.spine.internal.gradle.report.license.LicenseReporter

plugins {
    `maven-publish`
    kotlin("multiplatform")
    kotest
    `dokka-for-kotlin`
    `detekt-code-analysis`
    id("org.jetbrains.kotlinx.kover")
    `project-report`
}
apply<IncrementGuard>()
LicenseReporter.generateReportIn(project)

kotlin {
    explicitApi()

    jvm {
        withJava()
        compilations.all {
            kotlinOptions.jvmTarget = BuildSettings.javaVersion.toString()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies{
                api(Spine.reflect)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(Kotest.assertions)
                implementation(Kotest.frameworkEngine)
                implementation(Kotest.datatest)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(Flogger.lib)
                implementation(Guava.lib)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(Spine.testlib)
                implementation(JUnit.runner)
                implementation(Kotest.runnerJUnit5Jvm)
                implementation(SystemLambda.lib)
            }
        }
    }
}

detekt {
    source = files(
        "src/commonMain",
        "src/jvmMain"
    )
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    configureLogging()
}

tasks {
    withType<KotlinCompile>().configureEach {
        setFreeCompilerArgs()
    }
    registerTestTasks()
}

kover {
    useJacocoTool()
}

koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        if (name.contains("jvm", true)) {
            artifact(project.javadocJar())
        } else {
            artifact(project.dokkaKotlinJar())
        }
    }
}

JavadocConfig.applyTo(project)
