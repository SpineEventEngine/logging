/*
 * Copyright 2023, TeamDev. All rights reserved.
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

import com.google.common.flogger.AbstractLogger;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.LogSites;
import com.google.common.flogger.backend.Platform.LogCallerFinder;
import com.google.common.flogger.util.CallerFinder;

/**
 * Default caller finder implementation which should work on all recent Java releases.
 *
 * <p>See class documentation in {@link LogCallerFinder} for important implementation restrictions.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/StackBasedCallerFinder.java">
 *     Original Java code of Google Flogger</a>
 */
public final class StackBasedCallerFinder extends LogCallerFinder {
  private static final LogCallerFinder INSTANCE = new StackBasedCallerFinder();

  // Called during logging platform initialization; MUST NOT call any code that might log.
  public static LogCallerFinder getInstance() {
    return INSTANCE;
  }

  @Override
  public String findLoggingClass(Class<? extends AbstractLogger<?>> loggerClass) {
    // We can skip at most only 1 method from the analysis, the inferLoggingClass() method itself.
    StackTraceElement caller = CallerFinder.findCallerOf(loggerClass, 1);
    if (caller != null) {
      // This might contain '$' for inner/nested classes, but that's okay.
      return caller.getClassName();
    }
    throw new IllegalStateException("no caller found on the stack for: " + loggerClass.getName());
  }

  @Override
  public LogSite findLogSite(Class<?> loggerApi, int stackFramesToSkip) {
    // Skip an additional stack frame because we create the Throwable inside this method, not at
    // the point that this method was invoked (which allows completely alternate implementations
    // to avoid even constructing the Throwable instance).
    StackTraceElement caller = CallerFinder.findCallerOf(loggerApi, stackFramesToSkip + 1);
    // Returns INVALID if "caller" is null (no caller found for given API class).
    return LogSites.logSiteFrom(caller);
  }

  @Override
  public String toString() {
    return "Default stack-based caller finder";
  }

  private StackBasedCallerFinder() {}
}
