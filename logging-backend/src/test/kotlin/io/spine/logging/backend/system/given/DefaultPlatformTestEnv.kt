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

package io.spine.logging.backend.system.given

import com.google.common.flogger.AbstractLogger
import com.google.common.flogger.LogSite
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.Platform
import io.spine.logging.backend.system.BackendFactory
import io.spine.logging.backend.system.Clock
import com.google.common.flogger.testing.FakeLoggerBackend

/**
 * A primitive factory of [FakeLoggerBackend].
 */
internal class FakeBackendFactory : BackendFactory() {

    override fun create(loggingClassName: String?): LoggerBackend =
        FakeLoggerBackend(loggingClassName)
}

/**
 * A clock that always returns the configured [returnedTimestamp].
 */
internal class FixedTime : Clock() {

    /**
     * A timestamp that this clock always returns.
     */
    var returnedTimestamp = 0L

    override fun getCurrentTimeNanos(): Long = returnedTimestamp
}

/**
 * No-op implementation of [Platform.LogCallerFinder].
 */
internal class NoOpCallerFinder : Platform.LogCallerFinder() {

    /**
     * Throws [IllegalStateException].
     */
    override fun findLoggingClass(loggerClass: Class<out AbstractLogger<*>>?): String =
        throw UnsupportedOperationException()

    /**
     * Throws [IllegalStateException].
     */
    override fun findLogSite(loggerApi: Class<*>?, stackFramesToSkip: Int): LogSite =
        throw UnsupportedOperationException()
}
