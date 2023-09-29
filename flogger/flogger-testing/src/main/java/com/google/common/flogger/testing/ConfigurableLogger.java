/*
 * Copyright 2016, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package com.google.common.flogger.testing;

import io.spine.logging.flogger.AbstractLogger;
import io.spine.logging.flogger.LogContext;
import io.spine.logging.flogger.FloggerApi;
import io.spine.logging.flogger.backend.LoggerBackend;
import io.spine.logging.flogger.parser.DefaultPrintfMessageParser;
import io.spine.logging.flogger.parser.MessageParser;
import java.util.logging.Level;

/**
 * Helper class for unit tests, which need to test backend or context behavior.
 *
 * <p>Unlike normal logger instances, this one can be reconfigured dynamically.
 * It has specific methods for injecting timestamps and forcing log statements.
 *
 * <p>This class is mutable and not thread safe.
 *
 * @see <a href="https://rb.gy/smalv">Original Java code of Google Flogger</a>
 */
public final class ConfigurableLogger extends AbstractLogger<ConfigurableLogger.Api> {
  // Midnight Jan 1st, 2000 (GMT)
  private static final long DEFAULT_TIMESTAMP_NANOS = 946684800000000000L;

  public interface Api extends FloggerApi<Api> { }

  /** Returns a test logger for the default logging API. */
  public static ConfigurableLogger create(LoggerBackend backend) {
    return new ConfigurableLogger(backend);
  }

  /** Constructs a test logger with the given backend. */
  private ConfigurableLogger(LoggerBackend backend) {
    super(backend);
  }

  @Override
  public Api at(Level level) {
    return at(level, DEFAULT_TIMESTAMP_NANOS);
  }

  /** Logs at the given level, with the specified nanosecond timestamp. */
  @SuppressWarnings("GoodTime") // should accept a java.time.Instant
  public Api at(Level level, long timestampNanos) {
    return new TestContext(level, false, timestampNanos);
  }

  /** Forces logging at the given level. */
  public Api forceAt(Level level) {
    return forceAt(level, DEFAULT_TIMESTAMP_NANOS);
  }

  /** Forces logging at the given level, with the specified nanosecond timestamp. */
  @SuppressWarnings("GoodTime") // should accept a java.time.Instant
  public Api forceAt(Level level, long timestampNanos) {
    return new TestContext(level, true, timestampNanos);
  }

  /** Logging context implementing the basic logging API. */
  private final class TestContext extends LogContext<ConfigurableLogger, Api> implements Api {
    private TestContext(Level level, boolean isForced, long timestampNanos) {
      super(level, isForced, timestampNanos);
    }

    @Override
    protected ConfigurableLogger getLogger() {
      return ConfigurableLogger.this;
    }

    @Override
    protected Api api() {
      return this;
    }

    @Override
    protected Api noOp() {
      throw new UnsupportedOperationException(
          "There is no no-op implementation of the logging API for the testing logger.");
    }

    @Override
    protected MessageParser getMessageParser() {
      return DefaultPrintfMessageParser.getInstance();
    }
  }
}
