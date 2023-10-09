import io.spine.internal.dependency.Log4j2

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
    `jvm-module`
}

dependencies {
    testImplementation(Log4j2.core)
    testImplementation(project(":logging"))
    testImplementation(project(":fixtures"))
    testImplementation(project(":flogger-api"))

    /**
     * Adds `log4j2` backend and the default context to the classpath.
     *
     * The logging `Platform` discovers backend and context implementations
     * automatically via Java's `ServiceLoader`. A user doesn't need to
     * interact with “hard” classes from these dependencies. So, they are
     * usually added to [runtimeOnly] configuration.
     *
     * But for this test, it is important to make sure that the actually
     * discovered implementations match the test expectations. With a small
     * chance, but the `Platform` may surprisingly load another backend,
     * and it will pass all tests.
     *
     * So, we use “hard” classes from these dependencies to assert that
     * the actually loaded backend and context match the test expectations.
     */
    testImplementation(project(":logging-log4j2-backend"))
    testImplementation(project(":logging-tls-context"))
}
