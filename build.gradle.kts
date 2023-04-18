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

@file:Suppress("UNUSED_VARIABLE") // ... used for getting named objects.

import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.checkstyle.CheckStyleConfig
import io.spine.internal.gradle.javadoc.JavadocConfig
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import io.spine.internal.gradle.publish.IncrementGuard
import io.spine.internal.gradle.publish.PublishingRepos
import io.spine.internal.gradle.publish.javadocJar
import io.spine.internal.gradle.publish.spinePublishing
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.report.pom.PomGenerator
import io.spine.internal.gradle.standardToSpineSdk
import io.spine.internal.gradle.testing.configureLogging
import io.spine.internal.gradle.testing.registerTestTasks
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

plugins {
   `maven-publish`
    kotlin("multiplatform")
    `dokka-for-kotlin`
    idea
    `project-report`
    `detekt-code-analysis`
    `gradle-doctor`
    id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
}
apply(from = "$rootDir/version.gradle.kts")
apply<IncrementGuard>()
group = "io.spine"
version = rootProject.extra["versionToPublish"]!!

spinePublishing {
    // This is a single-module KMM project. The Kotlin plugin handles the publication.
    modulesWithCustomPublishing = setOf("logging")
    destinations = with(PublishingRepos) {
        setOf(
            cloudRepo,
            cloudArtifactRegistry,
            gitHub("logging")
        )
    }
    dokkaJar {
        java = false
    }
}

repositories.standardToSpineSdk()

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
                implementation(kotlin("test"))
                implementation(Kotest.assertions)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(Flogger.lib)
                runtimeOnly(Flogger.Runtime.systemBackend)
                implementation(Guava.lib)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(Spine.testlib)
            }
        }
    }
}

val jvmTest: Task by tasks.getting {
    (this as Test).run {
        useJUnitPlatform()
        configureLogging()
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        setFreeCompilerArgs()
    }
    withType<KotlinTest>().configureEach {
        reports.junitXml.required.set(true)
    }
    withType<KotlinJvmTest>().configureEach {
        reports.junitXml.required.set(true)
    }
    withType<Test>().configureEach {
        reports.junitXml.required.set(true)
    }
    registerTestTasks()
}

kover {
    useJacocoTool()
}

koverReport {
    xml {
        onCheck = true
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

CheckStyleConfig.applyTo(project)
// Apply Javadoc configuration here (and not right after the `plugins` block)
// because the `javadoc` task is added when the `kotlin` block `withJava` is applied.
JavadocConfig.applyTo(project)
PomGenerator.applyTo(project)
LicenseReporter.generateReportIn(project)
LicenseReporter.mergeAllReports(project)
