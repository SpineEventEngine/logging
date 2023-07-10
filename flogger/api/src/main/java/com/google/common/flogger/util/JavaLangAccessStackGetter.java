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

package com.google.common.flogger.util;

import static com.google.common.flogger.util.Checks.checkArgument;

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/**
 * {@link JavaLangAccess} based implementation of {@link StackGetter}.
 *
 * <p>Note. This is being compiled separate from the rest of the code, because it uses Java 8
 * private api.
 */
final class JavaLangAccessStackGetter implements StackGetter {
  private static final JavaLangAccess access = SharedSecrets.getJavaLangAccess();

  @Override
  public StackTraceElement callerOf(Class<?> target, int skipFrames) {
    Checks.checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    Throwable throwable = new Throwable();
    int index = findCallerIndex(throwable, target, skipFrames + 1);
    return index == -1 ? null : access.getStackTraceElement(throwable, index);
  }

  @Override
  public StackTraceElement[] getStackForCaller(Class<?> target, int maxDepth, int skipFrames) {
    Checks.checkArgument(maxDepth == -1 || maxDepth > 0, "maxDepth must be > 0 or -1");
    Checks.checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    Throwable throwable = new Throwable();
    int callerIndex = findCallerIndex(throwable, target, skipFrames + 1);
    if (callerIndex == -1) {
      return new StackTraceElement[0];
    }
    int elementsToAdd = access.getStackTraceDepth(throwable) - callerIndex;
    if (maxDepth > 0 && maxDepth < elementsToAdd) {
      elementsToAdd = maxDepth;
    }
    StackTraceElement[] stackTrace = new StackTraceElement[elementsToAdd];
    for (int i = 0; i < elementsToAdd; i++) {
      stackTrace[i] = access.getStackTraceElement(throwable, callerIndex + i);
    }
    return stackTrace;
  }

  private int findCallerIndex(Throwable throwable, Class<?> target, int skipFrames) {
    int depth = access.getStackTraceDepth(throwable);
    boolean foundCaller = false;
    String targetClassName = target.getName();
    for (int index = skipFrames; index < depth; index++) {
      if (access.getStackTraceElement(throwable, index).getClassName().equals(targetClassName)) {
        foundCaller = true;
      } else if (foundCaller) {
        return index;
      }
    }
    return -1;
  }
}
