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

package io.spine.logging.flogger.util;

import static io.spine.logging.flogger.util.Checks.checkNotNull;

import org.checkerframework.checker.nullness.qual.Nullable;

/** A helper class for determining callers of a specified class currently on the stack. */
public final class CallerFinder {

  private static final StackGetter STACK_GETTER = getBestStackGetter();

  /**
   * Returns the first available class implementing the {@link StackGetter} methods. The
   * implementation returned is dependent on the current java version.
   */
  private static StackGetter getBestStackGetter() {
    try {
      return new StackWalkerStackGetter();
    } catch (Throwable ignored) {
      // We may not be able to create `StackWalkerStackGetter` sometimes,
      // for example, on Android. This is not a problem because we have
      // `ThrowableStackGetter` as a fallback option.
      return new ThrowableStackGetter();
    }
  }

  /**
   * Returns the stack trace element of the immediate caller of the specified class.
   *
   * @param target the target class whose callers we are looking for.
   * @param skip the minimum number of calls known to have occurred between the first call to the
   *     target class and the point at which the specified throwable was created. If in doubt,
   *     specify zero here to avoid accidentally skipping past the caller. This is particularly
   *     important for code which might be used in Android, since you cannot know whether a tool
   *     such as Proguard has merged methods or classes and reduced the number of intermediate stack
   *     frames.
   * @return the stack trace element representing the immediate caller of the specified class, or
   *     null if no caller was found (due to incorrect target, wrong skip count or use of JNI).
   */
  @Nullable
  public static StackTraceElement findCallerOf(Class<?> target, int skip) {
    checkNotNull(target, "target");
    if (skip < 0) {
      throw new IllegalArgumentException("skip count cannot be negative: " + skip);
    }
    return STACK_GETTER.callerOf(target, skip + 1);
  }

  /**
   * Returns a synthetic stack trace starting at the immediate caller of the specified target.
   *
   * @param target the class who caller the returned stack trace will start at.
   * @param maxDepth the maximum size of the returned stack (pass -1 for the complete stack).
   * @param skip the minimum number of stack frames to skip before looking for callers.
   * @return a synthetic stack trace starting at the immediate caller of the specified target, or
   *     the empty array if no caller was found (due to incorrect target, wrong skip count or use of
   *     JNI).
   */
  public static StackTraceElement[] getStackForCallerOf(Class<?> target, int maxDepth, int skip) {
    checkNotNull(target, "target");
    if (maxDepth <= 0 && maxDepth != -1) {
      throw new IllegalArgumentException("invalid maximum depth: " + maxDepth);
    }
    if (skip < 0) {
      throw new IllegalArgumentException("skip count cannot be negative: " + skip);
    }
    return STACK_GETTER.getStackForCaller(target, maxDepth, skip + 1);
  }

  private CallerFinder() {}
}
