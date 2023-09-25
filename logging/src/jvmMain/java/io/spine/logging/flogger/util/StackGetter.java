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

package io.spine.logging.flogger.util;

/** Interface for finding call site information. */
interface StackGetter {
  /**
   * Returns the first caller of a method on the {@code target} class that is *not* a member of
   * {@code target} class, by walking back on the stack, or null if the {@code target} class cannot
   * be found or is the last element of the stack.
   *
   * @param target the class to find the caller of
   * @param skipFrames skip this many frames before looking for the caller. This can be used for
   *     optimization.
   */
  StackTraceElement callerOf(Class<?> target, int skipFrames);

  /**
   * Returns up to {@code maxDepth} frames of the stack starting at the stack frame that is a caller
   * of a method on {@code target} class but is *not* itself a method on {@code target} class.
   *
   * @param target the class to get the stack from
   * @param maxDepth the maximum depth of the stack to return. A value of -1 means to return the
   *     whole stack
   * @param skipFrames skip this many stack frames before looking for the target class. Used for
   *     optimization.
   * @throws IllegalArgumentException if {@code maxDepth} is 0 or < -1 or {@code skipFrames} is < 0.
   */
  StackTraceElement[] getStackForCaller(Class<?> target, int maxDepth, int skipFrames);
}
