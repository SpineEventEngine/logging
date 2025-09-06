/*
 * Copyright 2018, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.system;

import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ThreadSafe;
import io.spine.logging.backend.LogCallerFinder;
import io.spine.logging.AbstractLogger;
import io.spine.logging.LogSite;
import io.spine.logging.InjectedLogSite;
import kotlin.reflect.KClass;
import kotlin.jvm.JvmClassMappingKt;

import static io.spine.reflect.CallerFinder.findCallerOf;

/**
 * The default caller finder implementation for Java 9+.
 *
 * <p>See class documentation in {@link LogCallerFinder} for important
 * implementation restrictions.
 *
 * @see <a href="https://rb.gy/qozq3">Original Java code of Google Flogger</a> for historical context.
 */
@Immutable
@ThreadSafe
public final class StackBasedCallerFinder extends LogCallerFinder {
  private static final LogCallerFinder INSTANCE = new StackBasedCallerFinder();

  // Called during logging platform initialization; MUST NOT call any code that might log.
  public static LogCallerFinder getInstance() {
    return INSTANCE;
  }

  @Override
  public String findLoggingClass(KClass<? extends AbstractLogger<?>> loggerClass) {
    // Convert KClass to Java Class for compatibility with existing findCallerOf method
    Class<? extends AbstractLogger<?>> javaClass = JvmClassMappingKt.getJavaClass(loggerClass);
    // We can skip at most only 1 method from the analysis, the inferLoggingClass() method itself.
    var caller = findCallerOf(javaClass, 1);
    if (caller != null) {
      // This might contain '$' for inner/nested classes, but that's okay.
      return caller.getClassName();
    }
    throw new IllegalStateException("no caller found on the stack for: " + javaClass.getName());
  }

  @Override
  public LogSite findLogSite(KClass<?> loggerApi, int stackFramesToSkip) {
    // Convert KClass to Java Class for compatibility with existing findCallerOf method
    Class<?> javaClass = JvmClassMappingKt.getJavaClass(loggerApi);
    // Skip an additional stack frame because we create the Throwable inside this method, not at
    // the point that this method was invoked (which allows completely alternate implementations
    // to avoid even constructing the Throwable instance).
    var caller = findCallerOf(javaClass, stackFramesToSkip + 1);
    // Returns INVALID if "caller" is null (no caller found for given API class).
    if (caller == null) {
      return LogSite.Invalid.INSTANCE;
    }
    return new InjectedLogSite(
        caller.getClassName(),
        caller.getMethodName(),
        caller.getFileName(),
        caller.getLineNumber()
    );
  }

  @Override
  public String toString() {
    return "Default stack-based caller finder";
  }

  private StackBasedCallerFinder() {}
}
