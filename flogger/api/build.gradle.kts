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

plugins {
    `java-library`
}

sourceSets {
    // Although Flogger is a Java 8 library, some of its sources
    // can be compiled only with Java 11 or above.
    register("java11")
}

dependencies {
    api(sourceSets["java11"].output)

    implementation(project(":platform-generator", configuration = "generatedPlatformProvider"))
    implementation("org.checkerframework:checker-compat-qual:2.5.3")
    implementation("com.google.errorprone:error_prone_annotation:2.20.0")

    testImplementation(project(":api-testing"))
    testImplementation("junit:junit:4.13.1")
    testImplementation("com.google.truth:truth:1.1")
    testImplementation("org.mockito:mockito-core:2.28.2")

    val java11Implementation by configurations.getting
    java11Implementation("org.checkerframework:checker-compat-qual:2.5.3")
    java11Implementation("com.google.errorprone:error_prone_annotation:2.20.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    tasks.named<JavaCompile>("compileJava11Java") {
        val java11Compiler = project.javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
        javaCompiler.set(java11Compiler)
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}
