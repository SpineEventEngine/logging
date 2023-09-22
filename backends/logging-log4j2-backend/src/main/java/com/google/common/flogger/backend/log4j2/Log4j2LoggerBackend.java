/*
 * Copyright 2019, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package com.google.common.flogger.backend.log4j2;

import static com.google.common.flogger.backend.log4j2.Log4j2LogEventUtil.toLog4jLevel;
import static com.google.common.flogger.backend.log4j2.Log4j2LogEventUtil.toLog4jLogEvent;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import org.apache.logging.log4j.core.Logger;

/**
 * A logging backend that uses Log4j2 to output log statements.
 */
final class Log4j2LoggerBackend extends LoggerBackend {
  private final Logger logger;

  // VisibleForTesting
  Log4j2LoggerBackend(Logger logger) {
    this.logger = logger;
  }

  @Override
  public String getLoggerName() {
    return logger.getName();
  }

  @Override
  public boolean isLoggable(java.util.logging.Level level) {
    return logger.isEnabled(toLog4jLevel(level));
  }

  @Override
  public void log(LogData logData) {
    // The caller is responsible to call `isLoggable()` before calling
    // this method to ensure that only messages above the given
    // threshold are logged.
    logger.get().log(toLog4jLogEvent(logger.getName(), logData));
  }

  @Override
  public void handleError(RuntimeException error, LogData badData) {
    logger.get().log(toLog4jLogEvent(logger.getName(), error, badData));
  }
}
