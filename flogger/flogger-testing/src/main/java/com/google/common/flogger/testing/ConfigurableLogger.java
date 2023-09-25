/*
 * Copyright (C) 2016 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.testing;

import io.spine.logging.flogger.AbstractLogger;
import io.spine.logging.flogger.LogContext;
import io.spine.logging.flogger.LoggingApi;
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
 */
public final class ConfigurableLogger extends AbstractLogger<ConfigurableLogger.Api> {
  // Midnight Jan 1st, 2000 (GMT)
  private static final long DEFAULT_TIMESTAMP_NANOS = 946684800000000000L;

  public interface Api extends LoggingApi<Api> { }

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
