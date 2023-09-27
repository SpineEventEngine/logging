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

package io.spine.logging.backend.log4j2;

import static io.spine.logging.flogger.backend.MetadataProcessor.forScopeAndLogSite;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.WARNING;

import io.spine.logging.flogger.FloggerLogContext;
import io.spine.logging.flogger.FloggerLogSite;
import io.spine.logging.flogger.FloggerMetadataKey;
import io.spine.logging.flogger.backend.BaseMessageFormatter;
import io.spine.logging.flogger.backend.FloggerLogData;
import io.spine.logging.flogger.backend.MessageUtils;
import io.spine.logging.flogger.backend.Metadata;
import io.spine.logging.flogger.backend.MetadataHandler;
import io.spine.logging.flogger.backend.Platform;
import io.spine.logging.flogger.backend.SimpleMessageFormatter;
import io.spine.logging.flogger.context.ScopedLoggingContext;
import io.spine.logging.flogger.context.Tags;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;

/**
 * Helper to format {@link FloggerLogData}.
 */
final class Log4j2LogEventUtil {

  private Log4j2LogEventUtil() {}

  static LogEvent toLog4jLogEvent(String loggerName, FloggerLogData logData) {
    var metadata = forScopeAndLogSite(Platform.getInjectedMetadata(), logData.getMetadata());

    /*
     * If no configuration file could be located, Log4j2 will use the DefaultConfiguration. This
     * will cause logging output to go to the console and the context data will be ignored. This
     * mechanism can be used to detect if a configuration file has been loaded (or if the default
     * configuration was overwritten through the means of a configuration factory) by checking the
     * type of the current configuration class.
     *
     * Be aware that the LoggerContext class is not part of Log4j2's public API and behavior can
     * change with any minor release.
     *
     * For the future we are thinking about implementing a Flogger aware Log4j2 configuration (e.g.
     * using a configuration builder with a custom ConfigurationFactory) to configure a formatter,
     * which can perhaps be installed as default if nothing else is present. Then, we would not rely
     * on Log4j2 internals.
     */
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();
    String message;
    if (config instanceof DefaultConfiguration) {
      message = SimpleMessageFormatter.getDefaultFormatter().format(logData, metadata);
    } else {
      message =
          BaseMessageFormatter.appendFormattedMessage(logData, new StringBuilder()).toString();
    }

    Throwable thrown = metadata.getSingleValue(FloggerLogContext.Key.LOG_CAUSE);
    return toLog4jLogEvent(loggerName, logData, message, toLog4jLevel(logData.getLevel()), thrown);
  }

  static LogEvent toLog4jLogEvent(String loggerName, RuntimeException error, FloggerLogData badData) {
    String message = formatBadLogData(error, badData);
    // Re-target this log message as a warning (or above) since it indicates a real bug.
    Level level = badData.getLevel().intValue() < WARNING.intValue() ? WARNING : badData.getLevel();
    return toLog4jLogEvent(loggerName, badData, message, toLog4jLevel(level), error);
  }

  private static LogEvent toLog4jLogEvent(
      String loggerName,
      FloggerLogData logData,
      String message,
      org.apache.logging.log4j.Level level,
      Throwable thrown) {

    FloggerLogSite logSite = logData.getLogSite();
    StackTraceElement locationInfo =
        new StackTraceElement(
            logSite.getClassName(),
            logSite.getMethodName(),
            logSite.getFileName(),
            logSite.getLineNumber());

    return Log4jLogEvent.newBuilder()
        .setLoggerName(loggerName)
        .setLoggerFqcn(logData.getLoggerName())
        .setLevel(level) // this might be different from logData.getLevel() for errors.
        .setMessage(new SimpleMessage(message))
        .setThreadName(Thread.currentThread().getName())
        .setInstant(getInstant(logData.getTimestampNanos()))
        .setThrown(thrown != null ? Throwables.getRootCause(thrown) : null)
        .setIncludeLocation(true)
        .setSource(locationInfo)
        .setContextData(createContextMap(logData))
        .build();
  }

  @SuppressWarnings({"NanosTo_Seconds", "SecondsTo_Nanos"})
  private static Instant getInstant(long timestampNanos) {
    MutableInstant instant = new MutableInstant();
    // Don't use Duration here as (a) it allocates and (b) we can't allow error on overflow.
    long epochSeconds = NANOSECONDS.toSeconds(timestampNanos);
    int remainingNanos = (int) (timestampNanos - SECONDS.toNanos(epochSeconds));
    instant.initFromEpochSecond(epochSeconds, remainingNanos);
    return instant;
  }

  /** Converts java.util.logging.Level to org.apache.log4j.Level. */
  static org.apache.logging.log4j.Level toLog4jLevel(java.util.logging.Level level) {
    int logLevel = level.intValue();
    if (logLevel < java.util.logging.Level.FINE.intValue()) {
      return org.apache.logging.log4j.Level.TRACE;
    } else if (logLevel < java.util.logging.Level.INFO.intValue()) {
      return org.apache.logging.log4j.Level.DEBUG;
    } else if (logLevel < java.util.logging.Level.WARNING.intValue()) {
      return org.apache.logging.log4j.Level.INFO;
    } else if (logLevel < java.util.logging.Level.SEVERE.intValue()) {
      return org.apache.logging.log4j.Level.WARN;
    }
    return org.apache.logging.log4j.Level.ERROR;
  }

  /**
   * Formats the log message in response to an exception during a previous logging attempt. A
   * synthetic error message is generated from the original log data and the given exception is set
   * as the cause. The level of this record is the maximum of WARNING or the original level.
   */
  private static String formatBadLogData(RuntimeException error, FloggerLogData badLogData) {
    StringBuilder errorMsg =
        new StringBuilder("LOGGING ERROR: ").append(error.getMessage()).append('\n');
    int length = errorMsg.length();
    try {
      appendLogData(badLogData, errorMsg);
    } catch (RuntimeException e) {
      // Reset partially written buffer when an error occurs.
      errorMsg.setLength(length);
      errorMsg.append("Cannot append LogData: ").append(e);
    }
    return errorMsg.toString();
  }

  /** Appends the given {@link FloggerLogData} to the given {@link StringBuilder}. */
  private static void appendLogData(FloggerLogData data, StringBuilder out) {
    out.append("  original message: ");
    if (data.getTemplateContext() == null) {
      out.append(data.getLiteralArgument());
    } else {
      // We know that there's at least one argument to display here.
      out.append(data.getTemplateContext().getMessage());
      out.append("\n  original arguments:");
      for (Object arg : data.getArguments()) {
        out.append("\n    ").append(MessageUtils.safeToString(arg));
      }
    }
    Metadata metadata = data.getMetadata();
    if (metadata.size() > 0) {
      out.append("\n  metadata:");
      for (int n = 0; n < metadata.size(); n++) {
        out.append("\n    ");
        out.append(metadata.getKey(n).getLabel()).append(": ").append(metadata.getValue(n));
      }
    }
    out.append("\n  level: ").append(data.getLevel());
    out.append("\n  timestamp (nanos): ").append(data.getTimestampNanos());
    out.append("\n  class: ").append(data.getLogSite().getClassName());
    out.append("\n  method: ").append(data.getLogSite().getMethodName());
    out.append("\n  line number: ").append(data.getLogSite().getLineNumber());
  }

  private static final MetadataHandler<FloggerMetadataKey.KeyValueHandler> HANDLER =
      MetadataHandler.builder(Log4j2LogEventUtil::handleMetadata).build();

  private static void handleMetadata(
          FloggerMetadataKey<Object> key, Object value, FloggerMetadataKey.KeyValueHandler kvh) {
    if (key.getClass().equals(FloggerLogContext.Key.TAGS.getClass())) {
      processTags(key, value, kvh);
    } else {
      // In theory a user can define a custom tag and use it as a MetadataKey. Those
      // keys shall be treated in the same way as LogContext.Key.TAGS when used as a
      // MetadataKey. Might be removed if visibility of MetadataKey#clazz changes.
      if (value instanceof Tags) {
        processTags(key, value, kvh);
      } else {
        ValueQueue.appendValues(key.getLabel(), value, kvh);
      }
    }
  }

  private static void processTags(
          FloggerMetadataKey<Object> key, Object value, FloggerMetadataKey.KeyValueHandler kvh) {
    ValueQueue valueQueue = ValueQueue.appendValueToNewQueue(value);
    // Unlike single metadata (which is usually formatted as a single value), tags are always
    // formatted as a list.
    // Given the tags: tags -> foo=[bar], it will be formatted as tags=[foo=bar].
    ValueQueue.appendValues(
        key.getLabel(),
        valueQueue.size() == 1
            ? StreamSupport.stream(valueQueue.spliterator(), false).collect(Collectors.toList())
            : valueQueue,
        kvh);
  }

  /**
   * We do not support {@code MDC.getContext()} and {@code NDC.getStack()} and we do not make any
   * attempt to merge Log4j2 context data with Flogger's context data. Instead, users should use the
   * {@link ScopedLoggingContext}.
   *
   * <p>Flogger's {@link ScopedLoggingContext} allows to include additional metadata and tags into
   * logs which are written from current thread. This context data will be added to the log4j2
   * event.
   */
  private static StringMap createContextMap(FloggerLogData logData) {
    var metadataProcessor = forScopeAndLogSite(Platform.getInjectedMetadata(),
                                               logData.getMetadata());

    StringMap contextData = ContextDataFactory.createContextData(metadataProcessor.keyCount());
    metadataProcessor.process(
        HANDLER,
        (key, value) ->
            contextData.putValue(key, ValueQueue.maybeWrap(value, contextData.getValue(key))));

    contextData.freeze();

    return contextData;
  }
}
