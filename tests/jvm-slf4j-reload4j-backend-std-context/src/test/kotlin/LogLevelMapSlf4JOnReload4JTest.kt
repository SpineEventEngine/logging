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

import io.kotest.core.annotation.Ignored
import io.spine.logging.Level
import io.spine.logging.context.BaseLogLevelMapTest
import io.spine.testing.logging.Recorder

/**
 * This is a non-abstract integration test of [LogLevelMap][io.spine.logging.context.LogLevelMap]
 * executed in the project with SLF4J backend and `spine-logging-std-context`.
 * SLF4J uses Reload4J logging.
 *
 * Please see `build.gradle.kts` of this module for the details.
 */
@Ignored // Until recording for Reload4J is implemented.
@Suppress("unused") // Until SLF4J backend is added.
internal class LogLevelMapSlf4JOnReload4JTest: BaseLogLevelMapTest() {

    // TODO:2023-10-10:yevhenii.nadtochii: Make this test work when SLF4J backend is added.
    //  See issue: https://github.com/SpineEventEngine/logging/issues/77
    override fun createRecorder(loggerName: String, minLevel: Level): Recorder {
        error("Not implemented.")
    }
}
