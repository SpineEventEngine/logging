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

package io.spine.logging.context.grpc;

import io.spine.logging.flogger.LoggingScope;
import io.spine.logging.flogger.context.ContextMetadata;
import io.spine.logging.flogger.context.LogLevelMap;
import io.spine.logging.flogger.context.ScopeType;
import io.spine.logging.flogger.context.ScopedLoggingContext.ScopeList;
import io.spine.logging.flogger.context.Tags;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A mutable thread-safe holder for context scoped logging information.
 *
 * @see <a href="https://rb.gy/nfnwv">Original Java code of Google Flogger</a>
 */
final class GrpcContextData {

  static Tags getTagsFor(@Nullable GrpcContextData context) {
    if (context != null) {
      Tags tags = context.tagRef.get();
      if (tags != null) {
        return tags;
      }
    }
    return Tags.empty();
  }

  static ContextMetadata getMetadataFor(@Nullable GrpcContextData context) {
    if (context != null) {
      ContextMetadata metadata = context.metadataRef.get();
      if (metadata != null) {
        return metadata;
      }
    }
    return ContextMetadata.none();
  }

  static boolean shouldForceLoggingFor(
      @Nullable GrpcContextData context, String loggerName, Level level) {
    if (context != null) {
      LogLevelMap map = context.logLevelMapRef.get();
      if (map != null) {
        return map.getLevel(loggerName).intValue() <= level.intValue();
      }
    }
    return false;
  }

  @Nullable
  static LoggingScope lookupScopeFor(@Nullable GrpcContextData contextData, ScopeType type) {
    return contextData != null ? ScopeList.lookup(contextData.scopes, type) : null;
  }

  private abstract static class ScopedReference<T> {
    private final AtomicReference<T> value;

    ScopedReference(@Nullable T initialValue) {
      this.value = new AtomicReference<>(initialValue);
    }

    @Nullable
    final T get() {
      return value.get();
    }

    // Note: If we could use Java 1.8 runtime libraries, this would just be "accumulateAndGet()",
    // but gRPC is Java 1.7 compatible: https://github.com/grpc/grpc-java/blob/master/README.md
    final void mergeFrom(@Nullable T delta) {
      if (delta != null) {
        T current;
        do {
          current = get();
        } while (!value.compareAndSet(current, current != null ? merge(current, delta) : delta));
      }
    }

    abstract T merge(T current, T delta);
  }

  @Nullable private final ScopeList scopes;
  private final ScopedReference<Tags> tagRef;
  private final ScopedReference<ContextMetadata> metadataRef;
  private final ScopedReference<LogLevelMap> logLevelMapRef;
  // Only needed to register that log level maps are being used (as a performance optimization).
  private final GrpcContextDataProvider provider;

  GrpcContextData(
      @Nullable GrpcContextData parent,
      @Nullable ScopeType scopeType,
      GrpcContextDataProvider provider) {
    this.scopes = ScopeList.addScope(parent != null ? parent.scopes : null, scopeType);
    this.tagRef =
        new ScopedReference<Tags>(parent != null ? parent.tagRef.get() : null) {
          @Override
          Tags merge(Tags current, Tags delta) {
            return current.merge(delta);
          }
        };
    this.metadataRef =
        new ScopedReference<ContextMetadata>(parent != null ? parent.metadataRef.get() : null) {
          @Override
          ContextMetadata merge(ContextMetadata current, ContextMetadata delta) {
            return current.concatenate(delta);
          }
        };
    this.logLevelMapRef =
        new ScopedReference<LogLevelMap>(parent != null ? parent.logLevelMapRef.get() : null) {
          @Override
          LogLevelMap merge(LogLevelMap current, LogLevelMap delta) {
            return current.merge(delta);
          }
        };
    this.provider = provider;
  }

  void addTags(@Nullable Tags tags) {
    tagRef.mergeFrom(tags);
  }

  void addMetadata(@Nullable ContextMetadata metadata) {
    metadataRef.mergeFrom(metadata);
  }

  void applyLogLevelMap(@Nullable LogLevelMap logLevelMap) {
    if (logLevelMap != null) {
      // Set the global flag to trigger testing of the log level map from now on (we only apply a
      // log level map to an active context or one that's about to become active).
      provider.setLogLevelMapFlag();
      logLevelMapRef.mergeFrom(logLevelMap);
    }
  }
}
