/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm;

import io.spine.logging.jvm.backend.LoggerBackend;
import io.spine.logging.jvm.backend.Platform;
import io.spine.logging.jvm.parser.DefaultPrintfMessageParser;
import io.spine.logging.jvm.parser.MessageParser;

import java.util.logging.Level;

/**
 * The default implementation of {@link AbstractLogger} which returns the basic {@link MiddlemanApi}
 * and uses the default parser and system configured backend.
 *
 * <p>Note that when extending the logging API or specifying a new parser, you will need to create a
 * new logger class (rather than extending this one). Unlike the {@link LogContext} class,
 * which must be extended in order to modify the logging API, this class is not generified and thus
 * cannot be modified to produce a different logging API.
 *
 * <p>The choice to prevent direct extension of loggers was made to ensure that users
 * of a specific logger implementation always get the same behavior.
 *
 * @apiNote It is expected that this class is going to be merged
 *   with {@code io.spine.logging.JvmLogger} of the {@code logging} module.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/FluentLogger.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public final class Middleman extends AbstractLogger<Middleman.Api> {
  /**
   * The non-wildcard, fully specified, logging API for this logger. Fluent logger implementations
   * should specify a non-wildcard API like this with which to generify the abstract logger.
 *
   * <p>
   * It is possible to add methods to this logger-specific API directly, but it is recommended that
   * a separate top-level API and LogContext is created, allowing it to be shared by other
   * implementations.
   */
  public interface Api extends MiddlemanApi<Api> {}

  /**
   * The non-wildcard, fully specified, no-op API implementation. This is required to provide a
   * no-op implementation whose type is compatible with this logger's API.
   */
  private static final class NoOp extends MiddlemanApi.NoOp<Api> implements Api {}

  // Singleton instance of the no-op API. This variable is purposefully declared as an instance of
  // the NoOp type instead of the Api type. This helps ProGuard optimization recognize the type of
  // this field more easily. This allows ProGuard to strip away low-level logs in Android apps in
  // fewer optimization passes. Do not change this to 'Api', or any less specific type.
  // VisibleForTesting
  static final NoOp NO_OP = new NoOp();

  /**
   * Returns a new logger instance which parses log messages using printf format for the enclosing
   * class using the system default logging backend.
   */
  public static Middleman forEnclosingClass() {
    // NOTE: It is _vital_ that the call to "caller finder" is made directly inside the static
    // factory method. See getCallerFinder() for more information.
    var loggingClass = Platform.getCallerFinder().findLoggingClass(Middleman.class);
    return new Middleman(Platform.getBackend(loggingClass));
  }

  /**
   * Creates a new logger instance with the specified backend.
   *
   * @apiNote This constructor used to be package-private in the original Flogger implementation.
   *         This, in turn, required reflection-based creation of new instances of this class in
   *         {@code io.spine.logging.JvmLoggerFactoryKt}. Now, as we aggregate the Flogger code,
   *         we open the constructor for simplicity.
   */
  public Middleman(LoggerBackend backend) {
    super(backend);
  }

  @Override
  public Api at(Level level) {
    var loggerName = getName();
    var mappedLevel = Platform.getMappedLevel(loggerName);
    if (Level.OFF.equals(mappedLevel)) {
      return NO_OP;
    }
    var isLoggable = isLoggable(level);
    var isForced = Platform.shouldForceLogging(loggerName, level, isLoggable);
    return (isLoggable || isForced) ? new Context(level, isForced) : NO_OP;
  }

  /** Logging context implementing the fully specified API for this logger. */
  // VisibleForTesting
  final class Context extends LogContext<Middleman, Api> implements Api {
    private Context(Level level, boolean isForced) {
      super(level, isForced);
    }

    @Override
    protected Middleman getLogger() {
      return Middleman.this;
    }

    @Override
    protected Api api() {
      return this;
    }

    @Override
    protected Api noOp() {
      return NO_OP;
    }

    @Override
    protected MessageParser getMessageParser() {
      return DefaultPrintfMessageParser.getInstance();
    }
  }
}
