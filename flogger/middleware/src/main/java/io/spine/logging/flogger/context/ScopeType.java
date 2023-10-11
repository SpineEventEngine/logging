/*
 * Copyright 2019, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.flogger.context;

import static io.spine.logging.flogger.util.Checks.checkNotNull;

import io.spine.logging.flogger.LoggingScope;
import io.spine.logging.flogger.LoggingScopeProvider;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Singleton keys which identify different types of scopes which scoped contexts can be bound to.
 *
 * <p>To bind a context to a scope type, create the context with that type:
 *
 * <pre>{@code
 * ScopedLoggingContext.getInstance().newScope(REQUEST).run(() -> someTask(...));
 * }</pre>
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/context/ScopeType.java">
 *     Original Java code of Google Flogger</a>
 */
public final class ScopeType implements LoggingScopeProvider {
  /**
   * The built in "request" scope. This can be bound to a scoped context in order to provide a
   * distinct request scope for each context, allowing stateful logging operations (e.g. rate
   * limiting) to be scoped to the current request.
   *
   * <p>Enable a request scope using:
   *
   * <pre>{@code
   * ScopedLoggingContext.getInstance().newScope(REQUEST).run(() -> scopedMethod(x, y, z));
   * }</pre>
   *
   * which runs {@code scopedMethod} with a new "request" scope for the duration of the context.
   *
   * <p>Then use per-request rate limiting using:
   *
   * <pre>{@code
   * logger.atWarning().atMostEvery(5, SECONDS).per(REQUEST).log("Some error message...");
   * }</pre>
   *
   * Note that in order for the request scope to be applied to a log statement, the {@code
   * per(REQUEST)} method must still be called; just being inside the request scope isn't enough.
   */
  public static final ScopeType REQUEST = create("request");

  /**
   * Creates a new Scope type, which can be used as a singleton key to identify a scope during
   * scoped context creation or logging. Callers are expected to retain this key in a static field
   * or return it via a static method. Scope types have singleton semantics and two scope types with
   * the same name are <em>NOT</em> equivalent.
   *
   * @param name a debug friendly scope identifier (e.g. "my_batch_job").
   */
  public static ScopeType create(String name) {
    return new ScopeType(name);
  }

  private final String name;

  private ScopeType(String name) {
    this.name = checkNotNull(name, "name");
  }


  // Called by ScopedLoggingContext to make a new scope instance when a context is installed.
  LoggingScope newScope() {
    return LoggingScope.create(name);
  }

  @Nullable
  @Override
  public LoggingScope getCurrentScope() {
    return ContextDataProvider.getInstance().getScope(this);
  }
}
