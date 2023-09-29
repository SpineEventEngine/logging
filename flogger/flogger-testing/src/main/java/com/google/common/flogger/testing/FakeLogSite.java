/*
 * Copyright 2015, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package com.google.common.flogger.testing;

import io.spine.logging.flogger.FloggerLogSite;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simplified implementation of {@link FloggerLogSite} for testing.
 *
 * @see <a href="http://rb.gy/wal1a">Original Java code of Google Flogger</a>
 */
public final class FakeLogSite extends FloggerLogSite {
  private static final AtomicInteger uid = new AtomicInteger();

  /** Creates a fake log site (with plausible behavior) from the given parameters. */
  public static FloggerLogSite create(
      String className, String methodName, int lineNumber, String sourcePath) {
    return new FakeLogSite(className, methodName, lineNumber, sourcePath);
  }

  /** Creates a unique fake log site for use as a key when testing shared static maps. */
  public static FloggerLogSite unique() {
    return create("ClassName", "method_" + uid.incrementAndGet(), 123, "ClassName.java");
  }

  private final String className;
  private final String methodName;
  private final int lineNumber;
  private final String sourcePath;

  private FakeLogSite(String className, String methodName, int lineNumber, String sourcePath) {
    this.className = className;
    this.methodName = methodName;
    this.lineNumber = lineNumber;
    this.sourcePath = sourcePath;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  public int getLineNumber() {
    return lineNumber;
  }

  @Override
  public String getFileName() {
    return sourcePath;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FakeLogSite)) {
      return false;
    }
    var other = (FakeLogSite) obj;
    return Objects.equals(className, other.className)
        && Objects.equals(methodName, other.methodName)
        && lineNumber == other.lineNumber
        && Objects.equals(sourcePath, other.sourcePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className, methodName, lineNumber, sourcePath);
  }
}
