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

import io.spine.internal.dependency.Kotest
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.testing.configureLogging
import io.spine.internal.gradle.testing.registerTestTasks
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kmp-module`

    id("org.jetbrains.kotlinx.kover")
    kotest
    `project-report`
}
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
                implementation(kotlin("test")) // https://youtrack.jetbrains.com/issue/KT-58872
                implementation(project(":logging"))
                api(project(":testutil-logging"))
                api(Kotest.assertions)
                api(Kotest.frameworkApi)
                api(Kotest.frameworkEngine)
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
    registerTestTasks()
}

kover {
    useJacoco()
}

koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}
