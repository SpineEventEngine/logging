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

import io.spine.dependency.lib.AutoService
import io.spine.dependency.lib.AutoServiceKsp
import io.spine.dependency.local.Base
import io.spine.dependency.local.Reflect
import io.spine.gradle.java.disableLinters

plugins {
    `jvm-module`
    ksp
}

dependencies {
    implementation(Base.annotations)
    implementation(Reflect.lib)
    implementation(project(":logging"))
    implementation(project(":jul-backend"))
    testImplementation(AutoService.annotations)
    kspTest(AutoServiceKsp.processor)
}

java {
    disableLinters() // Due to non-migrated Flogger sources.
}


/**
 * Specifies explicit dependencies between tasks.
 *
 * Since Gradle 8, all task dependencies should be explicit. Gradle fails
 * the build if it detects a task that uses output from another task without
 * an explicit dependency on it.
 *
 * Plugin authors should adapt to this with time. Until then, we have to specify
 * the missed dependencies on our own.
 */
afterEvaluate {
    // `kaptKotlin` task is created after the configuration phase,
    // so we have to use the `afterEvaluate` block.
    val kspKotlin by tasks.existing
    @Suppress("unused")
    val dokkaHtml by tasks.existing {
        dependsOn(kspKotlin)
    }
}
