/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import org.jspecify.annotations.Nullable;

/**
 * A synthetic exception which can be attached to log statements when additional stack trace
 * information is required in log files or via tools such as ECatcher.
 * <p>
 * The name of this class may become relied upon implicitly by tools such as ECatcher. Do not
 * rename or move this class without checking for implicit in logging tools.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogSiteStackTrace.java">
 *     Original Java code of Google Flogger</a>
 */
public final class LogSiteStackTrace extends Exception {
  /**
   * Creates a synthetic exception to hold a call-stack generated for the log statement itself.
   * <p>
   * This exception is never expected to actually get thrown or caught at any point.
   *
   * @param cause the optional cause (set via withCause() in the log statement).
   * @param stackSize the requested size of the synthetic stack trace (actual trace can be shorter).
   * @param syntheticStackTrace the synthetic stack trace starting at the log statement.
   */
  LogSiteStackTrace(
      @Nullable Throwable cause, StackSize stackSize, StackTraceElement[] syntheticStackTrace) {
    super(stackSize.toString(), cause);
    // This takes a defensive copy, but there's no way around that. Note that we cannot override
    // getStackTrace() to avoid a defensive copy because that breaks stack trace formatting
    // (which doesn't call getStackTrace() directly). See b/27310448.
    setStackTrace(syntheticStackTrace);
  }

  // We override this because it gets called from the superclass constructor and we don't want
  // it to do any work (we always replace it immediately).
  @SuppressWarnings("UnsynchronizedOverridesSynchronized")
  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
