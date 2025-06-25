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

package io.spine.logging.jvm.util;

import java.io.Closeable;

/**
 * A threal local counter, incremented whenever a log statement is being processed by the
 * backend. If this value is greater than 1, then reentrant logging has occured, and some code may
 * behave differently to try and avoid issues such as unbounded recursion. Logging may even be
 * disabled completely if the depth gets too high.
 *
 * <p>This class is an internal detail and must not be used outside the core Flogger library.
 * Backends which need to know the recursion depth for any reason should call {@code
 * Platform.getCurrentRecursionDepth()}.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/util/RecursionDepth.java">
 *     Original Java code of Google Flogger</a>
 */
public final class RecursionDepth implements Closeable {
  private static final ThreadLocal<RecursionDepth> holder = new ThreadLocal<RecursionDepth>() {
    @Override
    protected RecursionDepth initialValue() {
      return new RecursionDepth();
    }
  };

  /** Do not call this method directly, use {@code Platform.getCurrentRecursionDepth()}. */
  public static int getCurrentDepth() {
    return holder.get().value;
  }

  /** Do not call this method directly, use {@code Platform.getCurrentRecursionDepth()}. */
  public int getValue() {
    return value;
  }

  /** Internal API for use by core Flogger library. */
  public static RecursionDepth enterLogStatement() {
    RecursionDepth depth = holder.get();
    // Can only reach 0 if it wrapped around completely or someone is manipulating the value badly.
    // We really don't expect 2^32 levels of recursion however, so assume it's a bug.
    if (++depth.value == 0) {
      throw new AssertionError("Overflow of RecursionDepth (possible error in core library)");
    }
    return depth;
  }

  private int value = 0;

  @Override
  public void close() {
    if (value > 0) {
      value -= 1;
      return;
    }
    // This should never happen if the only callers are inside core library.
    throw new AssertionError("Mismatched calls to RecursionDepth (possible error in core library)");
  }
}
