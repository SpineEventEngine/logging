/*
 * Copyright 2023, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.log4j2

import io.spine.logging.Level
import io.spine.logging.backend.LogData
import io.spine.logging.backend.LoggerBackend
import org.apache.logging.log4j.core.Logger

/**
 * A logging backend that uses Log4j2 to output log statements.
 */
internal class Log4j2LoggerBackend(private val logger: Logger) : LoggerBackend() {

    override val loggerName: String?
        get() = logger.name

    override fun isLoggable(level: Level): Boolean =
        logger.isEnabled(Log4j2LogEventUtil.toLog4jLevel(level))

    override fun log(logData: LogData) {
        // The caller must ensure isLoggable() is checked before calling this method.
        logger.get().log(Log4j2LogEventUtil.toLog4jLogEvent(logger.name, logData))
    }

    override fun handleError(error: RuntimeException, badData: LogData) {
        logger.get().log(Log4j2LogEventUtil.toLog4jLogEvent(logger.name, error, badData))
    }
}
