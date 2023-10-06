/*
 * Copyright 2014, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.jul;

import io.spine.logging.flogger.LogContext;
import io.spine.logging.flogger.backend.LogData;
import io.spine.logging.flogger.backend.Metadata;

import java.util.logging.LogRecord;

/**
 * An eagerly evaluating {@link LogRecord} which is created by the Fluent Logger frontend and can be
 * passed to a normal log {@link java.util.logging.Handler} instance for output.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/SimpleLogRecord.java">
 *     Original Java code of Google Flogger</a>
 */
public final class JulRecord extends AbstractJulRecord {
  /** Creates a {@link JulRecord} for a normal log statement from the given data. */
  public static JulRecord create(LogData data, Metadata scope) {
    return new JulRecord(data, scope);
  }

  /** @deprecated Use create(LogData data, Metadata scope) and pass scoped metadata in. */
  @Deprecated
  public static JulRecord create(LogData data) {
    return create(data, Metadata.empty());
  }

  /** Creates a {@link JulRecord} in the case of an error during logging. */
  public static JulRecord error(RuntimeException error, LogData data, Metadata scope) {
    return new JulRecord(error, data, scope);
  }

  /** @deprecated Use error(LogData data, Metadata scope) and pass scoped metadata in. */
  @Deprecated
  public static JulRecord error(RuntimeException error, LogData data) {
    return error(error, data, Metadata.empty());
  }

  private JulRecord(LogData data, Metadata scope) {
    super(data, scope);
    setThrown(getMetadataProcessor().getSingleValue(LogContext.Key.LOG_CAUSE));

    // Calling getMessage() formats and caches the formatted message in the AbstractLogRecord.
    //
    // IMPORTANT: Conceptually there's no need to format the log message here, since backends can
    // choose to format messages in different ways or log structurally, so it's not obviously a
    // win to format things here first. Formatting would otherwise be done by AbstractLogRecord
    // when getMessage() is called, and the results cached; so the only effect of being "lazy"
    // should be that formatting (and thus calls to the toString() methods of arguments) happens
    // later in the same log statement.
    //
    // However ... due to bad use of locking in core JDK log handler classes, any lazy formatting
    // of log arguments (i.e. in the Handler's "publish()" method) can be done with locks held,
    // and thus risks deadlock. We can mitigate the risk by formatting the message string early
    // (i.e. here). This is wasteful in cases where this message is never needed (e.g. structured
    // logging) but necessary when using many of the common JDK handlers (e.g. StreamHandler,
    // FileHandler etc.) and it's impossible to know which handlers are being used.
    String unused = getMessage();
  }

  private JulRecord(RuntimeException error, LogData data, Metadata scope) {
    // In the case of an error, the base class handles everything as there's no specific formatting.
    super(error, data, scope);
  }
}
