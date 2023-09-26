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

package io.spine.logging.flogger;

import static io.spine.logging.flogger.util.Checks.checkNotNull;
import static java.lang.Math.max;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A stack based log site which uses information from a given {@code StackTraceElement}.
 *
 * <p>Unlike truly unique injected log sites, StackBasedLogSite falls back to using the class name,
 * method name and line number for {@code equals()} and {@code hashcode()}. This makes it almost as
 * good as a globally unique instance in most cases, except if either of the following is true:
 *
 * <ul>
 *   <li>There are two log statements on a single line.
 *   <li>Line number information is stripped from the class.
 * </ul>
 *
 * <p>This class should not be used directly outside the core Flogger libraries. If you need to
 * generate a {@link LogSite} from a {@link StackTraceElement}, use {@link
 * LogSites#logSiteFrom(StackTraceElement) LogSites.logSiteFrom(myStackTaceElement)}.
 */
final class StackBasedLogSite extends LogSite {
  // StackTraceElement is unmodifiable once created.
  private final StackTraceElement stackElement;

  public StackBasedLogSite(StackTraceElement stackElement) {
    this.stackElement = checkNotNull(stackElement, "stack element");
  }

  @Override
  public String getClassName() {
    return stackElement.getClassName();
  }

  @Override
  public String getMethodName() {
    return stackElement.getMethodName();
  }

  @Override
  public int getLineNumber() {
    // Prohibit negative numbers (which can appear in stack trace elements) from being returned.
    return max(stackElement.getLineNumber(), LogSite.UNKNOWN_LINE);
  }

  @Override
  public String getFileName() {
    return stackElement.getFileName();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return (obj instanceof StackBasedLogSite)
        && stackElement.equals(((StackBasedLogSite) obj).stackElement);
  }

  @Override
  public int hashCode() {
    // Note that (unlike other log site implementations) this hash-code appears to include the
    // file name when creating a hashcode, but this should be the same every time a stack trace
    // element is created, so it shouldn't be a problem.
    return stackElement.hashCode();
  }
}
