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

import io.spine.logging.jvm.backend.Platform;
import org.jspecify.annotations.Nullable;

/**
 * Helper class to generate log sites for the current line of code. This class is deliberately
 * isolated (rather than having the method in {@link JvmLogSite} itself) because manual log site
 * injection is rare and by isolating it into a separate class may help encourage users to think
 * carefully about the issue.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogSites.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public final class JvmLogSites {
  /**
   * Returns a {@code LogSite} for the caller of the specified class. This can be used in
 * conjunction with the {@link MiddlemanApi#withInjectedLogSite(JvmLogSite)} method to implement
   * logging helper methods. In some platforms, log site determination may be unsupported, and in
   * those cases this method will always return the {@link JvmLogSite#invalid} instance.
 *
   * <p>
   * For example (in {@code MyLoggingHelper}):
   * <pre>{@code
   * public static void logAndSomethingElse(String message, Object... args) {
   *   logger.atInfo()
   *       .withInjectedLogSite(callerOf(MyLoggingHelper.class))
   *       .logVarargs(message, args);
   * }
   * }</pre>
 *
   * <p>
   * This method should be used for the simple cases where the class in which the logging occurs is
   * a public logging API. If the log statement is in a different class (not the public logging API)
   * and the {@code LogSite} instance needs to be passed through several layers, consider using
   * {@link #logSite()} instead to avoid too much "magic" in your code.
 *
   * <p>
   * You should also seek to ensure that any API used with this method "looks like a logging API".
   * It's no good if a log entry contains a class and method name which doesn't correspond to
   * anything the user can relate to. In particular, the API should probably always accept the log
   * message or at least some of its parameters, and should always have methods with "log" in their
   * names to make the connection clear.
 *
   * <p>
   * It is very important to note that this method can be very slow, since determining the log site
   * can involve stack trace analysis. It is only recommended that it is used for cases where
   * logging is expected to occur (e.g. {@code INFO} level or above). Implementing a helper method
   * for {@code FINE} logging is usually unnecessary (it doesn't normally need to follow any
   * specific "best practice" behavior).
 *
   * <p>
   * Note that even when log site determination is supported, it is not defined as to whether two
   * invocations of this method on the same line of code will produce the same instance, equivalent
   * instances or distinct instance. Thus you should never invoke this method twice in a single
   * statement (and you should never need to).
 *
   * <p>
   * Note that this method call may be replaced in compiled applications via bytecode manipulation
   * or other mechanisms to improve performance.
   *
   * @param loggingApi the logging API to be identified as the source of log statements (this must
   *        appear somewhere on the stack above the point at which this method is called).
 *
   * @return the log site of the caller of the specified logging API,
   *        or {@link JvmLogSite#invalid} if the logging API was not found.
   */
  public static JvmLogSite callerOf(Class<?> loggingApi) {
    // Can't skip anything here since someone could pass in LogSite.class.
    return Platform.getCallerFinder().findLogSite(loggingApi, 0);
  }

  /**
   * Returns a {@code LogSite} for the current line of code. This can be used in conjunction with
 * the {@link MiddlemanApi#withInjectedLogSite(JvmLogSite)} method to implement logging helper
   * methods. In some platforms, log site determination may be unsupported, and in those cases this
   * method will always return the {@link JvmLogSite#invalid} instance.
 *
   * <p>
   * For example (in {@code MyLoggingHelper}):
   * <pre>{@code
   * public static void logAndSomethingElse(LogSite logSite, String message, Object... args) {
   *   logger.atInfo()
   *       .withInjectedLogSite(logSite)
   *       .logVarargs(message, args);
   * }
   * }</pre>
   * where callers would do:
   * <pre>{@code
   * MyLoggingHelper.logAndSomethingElse(logSite(), "message...");
   * }</pre>
 *
   * <p>
   * Because this method adds an additional parameter and exposes a Flogger specific type to the
   * calling code, you should consider using {@link #callerOf(Class)} for simple logging
   * utilities.
 *
   * <p>
   * It is very important to note that this method can be very slow, since determining the log site
   * can involve stack trace analysis. It is only recommended that it is used for cases where
   * logging is expected to occur (e.g. {@code INFO} level or above). Implementing a helper method
   * for {@code FINE} logging is usually unnecessary (it doesn't normally need to follow any
   * specific "best practice" behavior).
 *
   * <p>
   * Note that even when log site determination is supported, it is not defined as to whether two
   * invocations of this method on the same line of code will produce the same instance, equivalent
   * instances or distinct instance. Thus you should never invoke this method twice in a single
   * statement (and you should never need to).
 *
   * <p>
   * Note that this method call may be replaced in compiled applications via bytecode manipulation
   * or other mechanisms to improve performance.
   *
   * @return the log site of the caller of this method.
   */
  public static JvmLogSite logSite() {
    // Don't call "callerOf()" to avoid making another stack entry.
    return Platform.getCallerFinder().findLogSite(JvmLogSites.class, 0);
  }

  /**
   * Returns a new {@code LogSite} which reflects the information in the given {@link
   * StackTraceElement}, or {@link JvmLogSite#invalid} if given {@code null}.
   *
   * <p>This method is useful when log site information is only available via an external API,
   * which returns {@link StackTraceElement}.
   */
  public static JvmLogSite logSiteFrom(@Nullable StackTraceElement e) {
    return e != null ? new StackBasedLogSite(e) : JvmLogSite.invalid;
  }

  private JvmLogSites() {}
}
