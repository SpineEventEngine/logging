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

import io.spine.internal.dependency.Log4j2
import io.spine.internal.gradle.javadoc.JavadocConfig
import io.spine.internal.gradle.publish.SpinePublishing
import io.spine.internal.gradle.publish.javadocJar
import io.spine.internal.gradle.publish.spinePublishing
import io.spine.internal.gradle.report.license.LicenseReporter

plugins {
    `maven-publish`
    kotlin("multiplatform")
    `dokka-for-kotlin`
    `detekt-code-analysis`
    kotest
    id("org.jetbrains.kotlinx.kover")
    `project-report`

}

group = "io.spine.tools"

// This module configures `spinePublishing` on its own to change a prefix
// specified by the root project.
spinePublishing {
    artifactPrefix = "spine-"
    destinations = rootProject.the<SpinePublishing>().destinations
    customPublishing = true
    dokkaJar {
        java = false
    }
}

kotlin {
    explicitApi()
    jvm {
        withJava()
        compilations.all {
            kotlinOptions.jvmTarget = "${BuildSettings.javaVersion}"
        }
    }
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":logging"))
            }
        }
        named("jvmMain") {
            dependencies {
                implementation(Log4j2.core)
            }
        }
    }
}

detekt {
    source.from(
        "src/commonMain",
        "src/jvmMain"
    )
}

kover {
    useJacoco()
    koverReport {
        defaults {
            xml {
                onCheck = true
            }
        }
    }
}

publishing.publications {
    named<MavenPublication>("kotlinMultiplatform") {
        artifact(project.dokkaKotlinJar())
    }
    named<MavenPublication>("jvm") {
        artifact(project.javadocJar())
    }
}

LicenseReporter.generateReportIn(project)
JavadocConfig.applyTo(project)
