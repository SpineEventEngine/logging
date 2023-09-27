/*
 * Copyright 2017, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

/**
 * Static utility methods for lazy argument evaluation in Flogger. The {@link #lazy(FloggerLazyArg)}
 * method allows lambda expressions to be "cast" to the {@link FloggerLazyArg} interface.
 *
 * <p>In cases where the log statement is strongly expected to always be enabled (e.g. unconditional
 * logging at warning or above) it may not be worth using lazy evaluation because any work required
 * to evaluate arguments will happen anyway.
 *
 * <p>If lambdas are available, users should prefer using this class rather than explicitly creating
 * {@code LazyArg} instances.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LazyArgs.java">
 *     Original Java code of Google Flogger</a>
 */
// TODO: Add other generally useful methods here, especially things which help non-lambda users.
public final class FloggerLazyArgs {
  /**
   * Coerces a lambda expression or method reference to return a lazily evaluated logging argument.
   * Pass in a compatible, no-argument, lambda expression or method reference to have it evaluated
   * only when logging will actually occur.
   *
   * <pre>{@code
   * logger.atFine().log("value=%s", lazy(() -> doExpensive()));
   * logger.atWarning().atMostEvery(5, MINUTES).log("value=%s", lazy(stats::create));
   * }</pre>
   *
   * Evaluation of lazy arguments occurs at most once, and always in the same thread from which the
   * logging call was made.
   *
   * <p>Note also that it is almost never suitable to make a {@code toString()} call "lazy" using
   * this mechanism and, in general, explicitly calling {@code toString()} on arguments which are
   * being logged is an error as it precludes the ability to log an argument structurally.
   */
  public static <T> FloggerLazyArg<T> lazy(FloggerLazyArg<T> lambdaOrMethodReference) {
    // This method is essentially a coercing cast for the functional interface to give the compiler
    // a target type to convert a lambda expression or method reference into.
    return checkNotNull(lambdaOrMethodReference, "lazy arg");
  }

  private FloggerLazyArgs() {}
}
