/*
 * Copyright 2022, TeamDev. All rights reserved.
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

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "spine-logging"

include(
    "logging",
    "testutil-logging",
)

includeBackend(
    "logging-log4j2-backend",
    "logging-jul-backend",
    "logging-probe-backend",
)

includeContext(
    "logging-grpc-context",
    "logging-std-context",
)

includePlatform(
    "jvm-default-platform"
)

includeTest(
    "fixtures",
    "jvm-our-backend-our-context",
    "jvm-our-backend-grpc-context",
    "jvm-log4j2-backend-our-context",
    "jvm-slf4j-jdk14-backend-our-context",
    "jvm-slf4j-reload4j-backend-our-context",
    "logging-smoke-test",
)

includeFlogger(
    "flogger-api",
    "flogger-platform-generator",
)

fun includeBackend(vararg modules: String) = includeTo("backends", modules)

fun includeContext(vararg modules: String) = includeTo("contexts", modules)

fun includePlatform(vararg modules: String) = includeTo("platforms", modules)

fun includeTest(vararg modules: String) = includeTo("tests", modules)

fun includeFlogger(vararg modules: String) = includeTo("flogger", modules)

fun includeTo(directory: String, modules: Array<out String>) = modules.forEach { name ->
    include(name)
    project(":$name").projectDir = file("$directory/$name")
}
