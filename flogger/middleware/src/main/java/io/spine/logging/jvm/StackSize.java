/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

/**
 * Enum values to be passed into {@link JvmApi#withStackTrace} to control
 * the maximum number of stack trace elements created.
 *
 * <p>Note that the precise value returned by {@link #getMaxDepth()} may change over time,
 * but it can be assumed that {@code SMALL <= MEDIUM <= LARGE <= FULL}.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/StackSize.java">
 *     Original Java code of Google Flogger</a>
 */
public enum StackSize {
  /**
   * Produces a small stack suitable for finer grained debugging. For performance reasons, this
   * is the only stack size suitable for log statements at level {@code INFO} or finer, but is may
   * also be useful for {@code WARNING} level log statements in cases where context is not as
   * important. For {@code SEVERE} log statements, it is advised to use a stack size of
   * {@link #MEDIUM} or above.
   * <p>
   * Requesting a small stack trace for log statements which occur under normal circumstances is
   * acceptable, but may affect performance. Consider using
   * {@link JvmApi#withStackTrace(StackSize)} in conjunction with rate limiting methods,
   * such as {@link JvmApi#atMostEvery(int, java.util.concurrent.TimeUnit)}, to mitigate
   * performance issues.
   * <p>
   * The current maximum size of a {@code SMALL} stack trace is 10 elements, but this may change.
   */
  SMALL(10),

  /**
   * Produces a medium sized stack suitable for providing contextual information for most log
   * statements at {@code WARNING} or above. There should be enough stack trace elements in a
   * {@code MEDIUM} stack to provide sufficient debugging context in most cases.
   * <p>
   * Requesting a medium stack trace for any log statements which can occur regularly under normal
   * circumstances is not recommended.
   * <p>
   * The current maximum size of a {@code MEDIUM} stack trace is 20 elements, but this may change.
   */
  MEDIUM(20),

  /**
   * Produces a large stack suitable for providing highly detailed contextual information.
   * This is most useful for {@code SEVERE} log statements which might be processed by external
   * tools and subject to automated analysis.
   * <p>
   * Requesting a large stack trace for any log statement which can occur under normal circumstances
   * is not recommended.
   * <p>
   * The current maximum size of a {@code LARGE} stack trace is 50 elements, but this may change.
   */
  LARGE(50),

  /**
   * Provides the complete stack trace. This is included for situations in which it is known that
   * the upper-most elements of the stack are definitely required for analysis.
   * <p>
   * Requesting a full stack trace for any log statement which can occur under normal circumstances
   * is not recommended.
   */
  FULL(-1),

  /**
   * Provides no stack trace, making the {@code withStackTrace()} method an effective no-op.
   * This is useful when your stack size is conditional. For example:
   * <pre> {@code
   *   logger.atWarning()
   *       .withStackTrace(showTrace ? StackSize.MEDIUM : StackSize.NONE)
   *       .log("message");
   * }</pre>
   */
  NONE(0);

  private final int maxDepth;

  StackSize(int value) {
    this.maxDepth = value;
  }

  /**
   * Returns the maximum stack depth to create when adding contextual stack information to a log
   * statement.
   * <p>
   * Note that the precise number of stack elements emitted for the enum values might change over
   * time, but it can be assumed that {@code NONE < SMALL <= MEDIUM <= LARGE <= FULL}.
   */
  int getMaxDepth() {
    return maxDepth;
  }
}
