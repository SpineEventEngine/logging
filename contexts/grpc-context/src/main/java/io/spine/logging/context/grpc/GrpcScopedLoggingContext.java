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

package io.spine.logging.context.grpc;

import static io.spine.logging.flogger.util.Checks.checkNotNull;

import io.spine.logging.flogger.FloggerMetadataKey;
import io.spine.logging.flogger.context.ContextMetadata;
import io.spine.logging.flogger.context.LogLevelMap;
import io.spine.logging.flogger.context.ScopeType;
import io.spine.logging.flogger.context.ScopedLoggingContext;
import io.spine.logging.flogger.context.Tags;
import io.grpc.Context;
import org.jspecify.annotations.Nullable;

/**
 * A {@link io.grpc.Context gRPC}-based implementation of {@link ScopedLoggingContext}.
 *
 * <p>This is a lazily loaded singleton instance returned from
 * {@link GrpcContextDataProvider#getContextApiSingleton()}, which provides
 * application code with a mechanism for controlling logging contexts.
 *
 * @see <a href="https://rb.gy/w1wyu">Original Java code of Google Flogger</a>
 */
final class GrpcScopedLoggingContext extends ScopedLoggingContext {

  private final GrpcContextDataProvider provider;

  GrpcScopedLoggingContext(GrpcContextDataProvider provider) {
    this.provider = provider;
  }

  @Override
  public ScopedLoggingContext.Builder newContext() {
    return newBuilder(null);
  }

  @Override
  public ScopedLoggingContext.Builder newContext(ScopeType scopeType) {
    return newBuilder(scopeType);
  }

  private ScopedLoggingContext.Builder newBuilder(@Nullable ScopeType scopeType) {
    return new ScopedLoggingContext.Builder() {
      @Override
      public LoggingContextCloseable install() {
        GrpcContextData newContextData =
            new GrpcContextData(GrpcContextDataProvider.currentContext(), scopeType, provider);
        newContextData.addTags(getTags());
        newContextData.addMetadata(getMetadata());
        newContextData.applyLogLevelMap(getLogLevelMap());
        return installContextData(newContextData);
      }
    };
  }

  private static LoggingContextCloseable installContextData(GrpcContextData newContextData) {
    // Capture these variables outside the lambda.
    Context newGrpcContext =
        Context.current().withValue(GrpcContextDataProvider.getContextKey(), newContextData);
    @SuppressWarnings("MustBeClosedChecker")
    Context prev = newGrpcContext.attach();
    return () -> newGrpcContext.detach(prev);
  }

  @Override
  public boolean addTags(Tags tags) {
    checkNotNull(tags, "tags");
    GrpcContextData context = GrpcContextDataProvider.currentContext();
    if (context != null) {
      context.addTags(tags);
      return true;
    }
    return false;
  }

  @Override
  public <T> boolean addMetadata(FloggerMetadataKey<T> key, T value) {
    // Serves as the null pointer check, and we don't care much about the extra allocation in the
    // case where there's no context, because that should be very rare (and the singleton is small).
    ContextMetadata metadata = ContextMetadata.singleton(key, value);
    GrpcContextData context = GrpcContextDataProvider.currentContext();
    if (context != null) {
      context.addMetadata(metadata);
      return true;
    }
    return false;
  }

  @Override
  public boolean applyLogLevelMap(LogLevelMap logLevelMap) {
    checkNotNull(logLevelMap, "log level map");
    GrpcContextData context = GrpcContextDataProvider.currentContext();
    if (context != null) {
      context.applyLogLevelMap(logLevelMap);
      return true;
    }
    return false;
  }
}
