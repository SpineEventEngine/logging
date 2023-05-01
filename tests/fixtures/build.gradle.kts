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

@file:Suppress("UNUSED_VARIABLE")

import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.testing.configureLogging
import io.spine.internal.gradle.testing.registerTestTasks
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    `detekt-code-analysis`
    id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
    kotest
    `project-report`
}
LicenseReporter.generateReportIn(project)

kotlin {
    jvm {
        withJava()
        compilations.all {
            kotlinOptions.jvmTarget = BuildSettings.javaVersion.toString()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies{
                implementation(project(":logging"))
                implementation(kotlin("test"))
                implementation(Kotest.assertions)
                implementation(Kotest.frameworkApi)
                implementation(Kotest.frameworkEngine)
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
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(Spine.testlib)

//                runtimeOnly(Flogger.Runtime.systemBackend)

//                runtimeOnly(Flogger.Runtime.log4j2Backend)

//                runtimeOnly(Flogger.Runtime.slf4JBackend)
//                runtimeOnly(Slf4J.jdk14)

//                runtimeOnly(Flogger.Runtime.slf4JBackend)
//                runtimeOnly(Slf4J.reload4j)

//                runtimeOnly(Flogger.Runtime.grpcContext)
//                runtimeOnly(Grpc.context)
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
    useJacocoTool()
}

koverReport {
    xml {
        onCheck = true
    }
}