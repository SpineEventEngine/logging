/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import io.spine.dependency.lib.AutoService
import io.spine.dependency.lib.AutoServiceKsp
import io.spine.dependency.lib.OpenTelemetryKotlin
import io.spine.gradle.publish.SpinePublishing
import io.spine.gradle.publish.spinePublishing
import io.spine.gradle.testing.registerTestTasks
import org.gradle.api.tasks.testing.Test

plugins {
    `kmp-module`
    `kmp-publish`
    ksp
}

// `kmp-module` publications are configured by `kmp-publish` (the `spinePublishing`
// extension does not yet support Kotlin Multiplatform). The artifact is published as
// `spine-logging-otel-backend`, matching the prefix used by the root project.
spinePublishing {
    artifactPrefix = "spine-logging-"
    destinations = rootProject.the<SpinePublishing>().destinations
    customPublishing = true
}

kotlin {
    @Suppress("unused") // Source set `val`s are used implicitly.
    sourceSets {
        val commonMain by getting {
            dependencies {
                // The Spine logging backend SPI.
                api(project(":logging"))

                // The OpenTelemetry Kotlin API. Exposed in the public API of
                // `OtelBackendSettings.use(...)`, hence `api` rather than `implementation`.
                api(OpenTelemetryKotlin.api)

                // The no-op `OpenTelemetry` used as the default until an instance is injected.
                implementation(OpenTelemetryKotlin.noop)
            }
        }
        val jvmMain by getting {
            dependencies {
                // `@AutoService` registers the JVM `BackendFactory` for `ServiceLoader`.
                implementation(AutoService.annotations)
            }
        }
        val jvmTest by getting {
            dependencies {
                // The native Kotlin OpenTelemetry SDK, used only by tests to build an
                // `OpenTelemetry` instance with a recording log-record processor.
                implementation(OpenTelemetryKotlin.core)
                implementation(OpenTelemetryKotlin.implementation)

                implementation(project(":logging-testlib"))

                // Provides `DefaultPlatform`, which discovers the `@AutoService` factory,
                // so the full Spine → backend path can be exercised end-to-end.
                runtimeOnly(project(":jvm-default-platform"))
            }
        }
    }
}

dependencies {
    // KSP generates the `META-INF/services` registration from `@AutoService` on the
    // JVM target. In a KMP module the configuration is target-suffixed (`kspJvm`).
    add("kspJvm", AutoServiceKsp.processor)
}

// Registers the `fastTest`/`slowTest` tasks and the `*Spec`/`*Test` filter,
// matching the core `logging` module.
tasks.registerTestTasks()

// The `kmp-module` convention does not put the JUnit Platform on the JVM test
// task (it configures only the `jvm-module` `test` task), so enable it here.
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
