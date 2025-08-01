/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

import io.spine.dependency.lib.Asm
import io.spine.gradle.java.disableLinters

plugins {
    `jvm-module`
    application
}

dependencies {
    implementation(Asm.lib)
}

java {
    disableLinters() // Due to non-migrated Flogger sources.
}

tasks {
    register<JavaExec>("generatePlatformProvider") {
        mainClass.set("io.spine.logging.backend.generator.PlatformProviderGenerator")

        val outputJar = "build/provider/platform-provider.jar"
        args(listOf(outputJar))
        outputs.file(outputJar)
        doFirst { file(outputJar).deleteRecursively() }

        val inputClasspath = sourceSets.main.get().runtimeClasspath
        classpath(inputClasspath)
        inputs.files(inputClasspath)
    }
}

configurations {
    register("generatedPlatformProvider") {
        val genTask = tasks.named("generatePlatformProvider")
        outgoing.artifact(genTask)
    }
}
