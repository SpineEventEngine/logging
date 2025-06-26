/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.context.grpc;

import io.spine.logging.jvm.LoggingScope;
import io.spine.logging.jvm.context.ContextDataProvider;
import io.spine.logging.jvm.context.ContextMetadata;
import io.spine.logging.jvm.context.ScopeType;
import io.spine.logging.jvm.context.ScopedLoggingContext;
import io.spine.logging.jvm.context.Tags;
import io.grpc.Context;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * A {@link io.grpc.Context gRPC}-based implementation of {@link ContextDataProvider}.
 *
 * <p>When using {@code DefaultPlatform}, this provider will automatically
 * be used if it is included on the classpath, and no other implementation
 * of {@code ContextDataProvider} other than the default implementation is.
 *
 * <p>To specify it more explicitly or to work around an issue where multiple
 * {@code ContextDataProvider} implementations are on the classpath, one can set
 * the {@code flogger.logging_context} system property to the fully-qualified name
 * of this class:
 *
 * <ul>
 *   <li>{@code flogger.logging_context=io.spine.logging.context.grpc.GrpcContextDataProvider}
 * </ul>
 *
 * @see <a href="https://rb.gy/0cy88">Original Java code of Google Flogger</a> for historical context.
 */
public final class GrpcContextDataProvider extends ContextDataProvider {

  // For use by GrpcScopedLoggingContext (same package). We cannot define the keys in there because
  // this class must not depend on GrpcScopedLoggingContext during static initialization. We must
  // also delay initializing this value (via a lazy-holder) to avoid any risks during logger
  // initialization.
  static Context.Key<GrpcContextData> getContextKey() {
    return KeyHolder.GRPC_SCOPE;
  }

  /** Returns the current context data, or {@code null} if we are not in a context. */
  @Nullable
  static GrpcContextData currentContext() {
    return getContextKey().get();
  }

  // This is created lazily to avoid requiring it to be initiated at the same time as
  // GrpcContextDataProvider (which is created as the Platform instance is initialized). By doing
  // this we break any initialization cycles and allow the config API perform its own logging if
  // necessary.
  private volatile GrpcScopedLoggingContext configInstance = null;

  // When this is false we can skip some work for every log statement. This is set to true if _any_
  // context adds a log level map at any point (this is generally rare and only used for targeted
  // debugging so will often never occur during normal application use). This is never reset.
  private volatile boolean hasLogLevelMap = false;

  // A public no-arg constructor is necessary for use by ServiceLoader
  public GrpcContextDataProvider() {}

  /** Sets the flag to enable checking for a log level map after one is set for the first time. */
  void setLogLevelMapFlag() {
    hasLogLevelMap = true;
  }

  @Override
  public ScopedLoggingContext getContextApiSingleton() {
    GrpcScopedLoggingContext result = configInstance;
    if (result == null) {
      // GrpcScopedLoggingContext is stateless, so we shouldn't need double-checked locking here to
      // ensure we don't make more than one.
      result = new GrpcScopedLoggingContext(this);
      configInstance = result;
    }
    return result;
  }

  @Override
  public Tags getTags() {
    return GrpcContextData.getTagsFor(currentContext());
  }

  @Override
  public ContextMetadata getMetadata() {
    return GrpcContextData.getMetadataFor(currentContext());
  }

  @Nullable
  @Override
  public LoggingScope getScope(ScopeType type) {
    return GrpcContextData.lookupScopeFor(currentContext(), type);
  }

  @Override
  public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabledByLevel) {
    // Shortcutting boolean saves doing any work in the commonest case (this code is called for
    // every log statement, which is 100-1000 times more than just the  enabled log statements).
    return hasLogLevelMap
        && GrpcContextData.shouldForceLoggingFor(currentContext(), loggerName, level);
  }

  @Override
  public @Nullable Level getMappedLevel(String loggerName) {
    if (!hasLogLevelMap) {
      return null;
    }
    var context = currentContext();
    if (context == null) {
      return null;
    }
    var result = context.getMappedLevel(loggerName);
    return result;
  }

  // Static lazy-holder to avoid needing to call unknown code during Flogger initialization. While
  // gRPC context keys don't trigger any logging now, it's not certain that this is guaranteed.
  private static final class KeyHolder {
    private static final Context.Key<GrpcContextData> GRPC_SCOPE =
        Context.key("Flogger gRPC scope");

    private KeyHolder() {}
  }
}
