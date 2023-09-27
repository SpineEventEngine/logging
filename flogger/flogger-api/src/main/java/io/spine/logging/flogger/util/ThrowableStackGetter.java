/*
 * Copyright 2021, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

import static io.spine.logging.flogger.util.Checks.checkArgument;

/**
 * Default implementation of {@link StackGetter} using {@link Throwable#getStackTrace}.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/util/ThrowableStackGetter.java">
 *     Original Java code of Google Flogger</a>
 */
final class ThrowableStackGetter implements StackGetter {

  @Override
  public StackTraceElement callerOf(Class<?> target, int skipFrames) {
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    StackTraceElement[] stack = new Throwable().getStackTrace();
    int callerIndex = findCallerIndex(stack, target, skipFrames + 1);
    if (callerIndex != -1) {
      return stack[callerIndex];
    }

    return null;
  }

  @Override
  public StackTraceElement[] getStackForCaller(Class<?> target, int maxDepth, int skipFrames) {
    checkArgument(maxDepth == -1 || maxDepth > 0, "maxDepth must be > 0 or -1");
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    StackTraceElement[] stack = new Throwable().getStackTrace();
    int callerIndex = findCallerIndex(stack, target, skipFrames + 1);
    if (callerIndex == -1) {
      return new StackTraceElement[0];
    }
    int elementsToAdd = stack.length - callerIndex;
    if (maxDepth > 0 && maxDepth < elementsToAdd) {
      elementsToAdd = maxDepth;
    }
    StackTraceElement[] stackTrace = new StackTraceElement[elementsToAdd];
    System.arraycopy(stack, callerIndex, stackTrace, 0, elementsToAdd);
    return stackTrace;
  }

  private int findCallerIndex(StackTraceElement[] stack, Class<?> target, int skipFrames) {
    boolean foundCaller = false;
    String targetClassName = target.getName();
    for (int frameIndex = skipFrames; frameIndex < stack.length; frameIndex++) {
      if (stack[frameIndex].getClassName().equals(targetClassName)) {
        foundCaller = true;
      } else if (foundCaller) {
        return frameIndex;
      }
    }
    return -1;
  }
}
