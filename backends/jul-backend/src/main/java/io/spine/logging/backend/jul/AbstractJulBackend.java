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

package io.spine.logging.backend.jul;

import io.spine.logging.jvm.backend.LoggerBackend;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * An abstract implementation of {@code java.util.logging} (JUL) based backend.
 *
 * <p>This class handles everything except formatting of a log message
 * and metadata.
 *
 * @see <a href="https://rb.gy/jzz7x">Original Java code of Google Flogger</a> for historical context.
 */
public abstract class AbstractJulBackend extends LoggerBackend {
  // Set if any attempt at logging via the "forcing" logger fails due to an inability to set the
  // log level in the forcing logger. This result is cached so we don't repeatedly trigger
  // security exceptions every time something is logged. This field is only ever read or written
  // to in cases where the LogManager returns subclasses of Logger.
  private static volatile boolean cannotUseForcingLogger = false;

  private final Logger logger;

  // Internal constructor used by legacy callers - should be updated to just pass in the logging
  // class name. This needs work to handle anonymous loggers however (if that's ever supported).
  // TODO:2023-09-14:yevhenii.nadtochii: Should become `internal` when migrated to Kotlin.
  // See issue: https://github.com/SpineEventEngine/logging/issues/47
  protected AbstractJulBackend(Logger logger) {
    this.logger = logger;
  }

  /**
   * Constructs an abstract backend for the given class name.
   *
   * <p>Nested or inner class names (containing {@code $} are converted to names matching the
   * standard JDK logger namespace by converting '$' to '.', but in future it is expected that
   * nested and inner classes (especially anonymous ones) will have their names truncated to just
   * the outer class name. There is no benefit to having loggers named after an inner/nested
   * classes, and this distinction is expected to go away.
   */
  protected AbstractJulBackend(String loggingClass) {
    // TODO(b/27920233): Strip inner/nested classes when deriving logger name.
    this(Logger.getLogger(loggingClass.replace('$', '.')));
  }

  @Override
  public final String getLoggerName() {
    return logger.getName();
  }

  @Override
  public final boolean isLoggable(Level level) {
    return logger.isLoggable(level);
  }

  /**
   * Logs the given record using this backend. If {@code wasForced} is set, the backend will make a
   * best effort attempt to bypass any log level restrictions in the underlying Java {@link Logger},
   * but there are circumstances in which this can fail.
   */
  public final void log(LogRecord record, boolean wasForced) {
    // Q: Why is the code below so complex ?
    //
    // The code below is (sadly) necessarily complex due to the need to cope with the possibility
    // that the JDK logger instance we have is a subclass generated from a custom LogManager.
    //
    // Because the API docs for the "log(LogRecord)" method say that it can be overridden to capture
    // all logging, we must call it if there's a chance it was overridden.
    //
    // However we cannot always call it directly if the log statement was forced because the default
    // implementation of "log(LogRecord)" will will perform its own loggability check based only on
    // the log level, and could discard forced log records. But at the same time, we must ensure
    // that any handlers attached to that logger (and any parent loggers) will see all the log
    // records, including "forced" ones.
    //
    // The only vaguely sane approach to this is to use a child logger when forcing is required.
    //
    // It seems reasonable to assume that if the logger we have is some special subclass (which
    // might override the log(LogRecord) method) then any child logger we get from the LogManager
    // will be overridden in the same way. Thus logging to our logger and logging to a child logger
    // (which contains no additional handlers or filters) will have exactly the same effect, apart
    // from the loggability check.

    // Do the fast boolean check (which normally succeeds) before calling isLoggable().
    if (!wasForced || logger.isLoggable(record.getLevel())) {
      // Unforced log statements or forced log statements at or above the logger's level can be
      // passed to the normal log(LogRecord) method.
      logger.log(record);
    } else {
      // If logging has been forced for a log record which would otherwise be discarded, we cannot
      // call our logger's log(LogRecord) method, so we must simulate its behavior in one of two
      // ways.
      // 1: Simulate the effect of calling the log(LogRecord) method directly (this is safe if the
      //    logger provided by the log manager was a normal Logger instance).
      // 2: Obtain a "child" logger from the log manager which is set to log everything, and call
      //    its log(LogRecord) method instead (which should have the same overridden behavior and
      //    will still publish log records to our logger's handlers).
      //
      // In all cases we still call the filter (if one exists) even though we ignore the result.
      // Use a local variable to avoid race conditions where the filter can be unset at any time.
      Filter filter = logger.getFilter();
      if (filter != null) {
        filter.isLoggable(record);
      }
      if (logger.getClass() == Logger.class || cannotUseForcingLogger) {
        // If the Logger instance is not a subclass, its log(LogRecord) method cannot have been
        // overridden. That means it's safe to just publish the log record directly to handlers
        // to avoid the loggability check, which would otherwise discard the forced log record.
        publish(logger, record);
      } else {
        // Hopefully rare situation in which the logger is subclassed _and_ the log record would
        // normally be discarded based on its level.
        forceLoggingViaChildLogger(record);
      }
    }
  }

  // Documentation from public Java API documentation for java.util.logging.Logger:
  // ----
  // It will then call a Filter (if present) to do a more detailed check on whether the record
  // should be published. If that passes it will then publish the LogRecord to its output
  // Handlers. By default, loggers also publish to their parent's Handlers, recursively up the
  // tree.
  // ----
  // The question of whether filtering is also done recursively of parent loggers seems to be
  // answered by the documentation for setFilter(), which states:
  //   ""Set a filter to control output on _this_ Logger.""
  // which implies that only the immediate filter should be checked before publishing recursively.
  //
  // Note: Normally it might be important to care about the number of stack frames being created
  // if the log site information is inferred by the handlers (a handler at the root of the tree
  // would get a lot of extra stack frames to search through). However for Flogger, the LogSite
  // was already determined in the "shouldLog()" method because it's needed for things like
  // rate limiting. Thus we don't have to care about using iterative methods vs recursion here.
  private static void publish(Logger logger, LogRecord record) {
    // Annoyingly this method appears to copy the array every time it is called, but there's
    // nothing much we can do about this (and there could be synchronization issues even if we
    // could access things directly because handlers can be changed at any time). Most of the
    // time this returns the singleton empty array however, so it's not as bad as all that.
    for (Handler handler : logger.getHandlers()) {
      handler.publish(record);
    }
    if (logger.getUseParentHandlers()) {
      logger = logger.getParent();
      if (logger != null) {
        publish(logger, record);
      }
    }
  }

  // WARNING: This code will fail for anonymous loggers (getName() == null) and when Flogger
  // supports anonymous loggers it must ensure that this code path is avoided by not allowing
  // subclasses of Logger to be used.
  void forceLoggingViaChildLogger(LogRecord record) {
    // Assume that nobody else will configure or manipulate loggers with this "secret" name.
    Logger forcingLogger = getForcingLogger(logger);

    // This logger can be garbage collected at any time, so we must always reset any configuration.
    // This code is subject to a bunch of unlikely race conditions if the logger is manipulated
    // while these checks are being made, but there is nothing we can really do about this (the
    // setting of log levels is not protected by synchronizing the logger instance).
    try {
      forcingLogger.setLevel(Level.ALL);
    } catch (SecurityException e) {
      // If we're blocked from changing logging configuration then we cannot log "forced" log
      // statements via the forcingLogger. Fall back to publishing them directly to the handlers
      // (which may bypass logic present in an overridden log(LogRecord) method).
      cannotUseForcingLogger = true;
      // Log to the root logger to bypass any configuration that might drop this message.
      Logger.getLogger("")
          .log(
              Level.SEVERE,
              "Forcing log statements with Flogger has been partially disabled.\n"
                  + "The Flogger library cannot modify logger log levels, which is necessary to"
                  + " force log statements. This is likely due to an installed SecurityManager.\n"
                  + "Forced log statements will still be published directly to log handlers, but"
                  + " will not be visible to the 'log(LogRecord)' method of Logger subclasses.\n");
      publish(logger, record);
      return;
    }
    // Assume any custom behaviour in our logger instance also exists for a child logger.
    forcingLogger.log(record);
  }

  // Pass in the logger (even though it's in our instance) so that it's accessible for testing
  // without needing to make the field accessible.
  // VisibleForTesting
  // TODO:2023-09-14:yevhenii.nadtochii: Should become `internal` when migrated to Kotlin.
  // See issue: https://github.com/SpineEventEngine/logging/issues/47
  protected Logger getForcingLogger(Logger parent) {
    return Logger.getLogger(parent.getName() + ".__forced__");
  }
}
