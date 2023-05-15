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

package given.map

import given.map.CustomLoggingLevel.CRASH
import given.map.CustomLoggingLevel.TRACE
import io.spine.logging.Level
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.WARNING
import io.spine.logging.WithLogging


/**
 * A test fixture for stubbing logging operations.
 *
 * Extending classes must provide parameterless constructors because
 * instances of these fixtures are created via Kotlin reflection using
 * a class reference.
 */
abstract class LoggingTestFixture : WithLogging {

    fun atTrace() = logAt(TRACE)
    fun atDebug() = logAt(DEBUG)
    fun atInfo() = logAt(INFO)
    fun atWarning() = logAt(WARNING)
    fun atError() = logAt(ERROR)
    fun atCrash() = logAt(CRASH)
    
    private fun logAt(level: Level) {
        logger.at(level).log {
            "Stub logging message at `${level.name}`."
        }
    }
}

public object CustomLoggingLevel {

    public val TRACE: Level = Level("TRACE", 500)

    public val CRASH: Level = Level("CRASH", 1100)
}
