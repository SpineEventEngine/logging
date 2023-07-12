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
    `jvm-test-suite`
}

dependencies {
    implementation(project(":platform-generator", "generated-platform-provider"))
    implementation(project(":util"))

    implementation("org.checkerframework:checker-compat-qual:2.5.3")
    implementation("com.google.errorprone:error_prone_annotation:2.20.0")

    testImplementation("junit:junit:4.13.1")
    testImplementation("com.google.truth:truth:1.1")
    testImplementation("org.mockito:mockito-core:2.28.2")
}

java {
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(8)
    )
}

testing {
    val isolatedTest = "**/DefaultPlatformServiceLoadingTest.java"
    val test by suites.getting(JvmTestSuite::class) {
        useJUnit()
        sources.java.exclude(isolatedTest)
    }
    val serviceLoadingTest by suites.registering(JvmTestSuite::class) {
        useJUnit()

        dependencies {
            implementation(project(":api"))
            implementation("com.google.truth:truth:1.1")
            implementation("com.google.auto.service:auto-service:1.0")
            annotationProcessor("com.google.auto.service:auto-service:1.0")
        }

        sources.java.setSrcDirs(test.sources.java.srcDirs)
        sources.java.include(isolatedTest)
    }
}

tasks {
    test.configure {
        dependsOn(named("serviceLoadingTest"))
    }
}