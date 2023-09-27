/*
 * Copyright 2012, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.system;

import io.spine.logging.flogger.backend.FloggerLogData;
import io.spine.logging.flogger.backend.Platform;

import java.util.logging.Logger;

/**
 * A logging backend that uses the {@code java.util.logging} classes to output log statements.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/SimpleLoggerBackend.java">
 *     Original Java code of Google Flogger</a>
 */
public class SimpleLoggerBackend extends AbstractBackend {

  public SimpleLoggerBackend(Logger logger) {
    super(logger);
  }

  @Override
  public void log(FloggerLogData data) {
    log(SimpleLogRecord.create(data, Platform.getInjectedMetadata()), data.wasForced());
  }

  @Override
  public void handleError(RuntimeException error, FloggerLogData badData) {
    log(SimpleLogRecord.error(error, badData, Platform.getInjectedMetadata()), badData.wasForced());
  }
}
