/*
 * Copyright 2016, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.system;

import io.spine.logging.flogger.context.ContextDataProvider;
import io.spine.logging.flogger.context.LogLevelMap;
import io.spine.logging.flogger.context.ScopeType;
import io.spine.logging.flogger.context.ScopedLoggingContext;
import io.spine.logging.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import io.spine.logging.flogger.context.Tags;

/**
 * @deprecated Replaced by ContextDataProvider.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/LoggingContext.java">
 *     Original Java code of Google Flogger</a>
 */
// TODO(b/173778154): Delete this class once nothing external relies on it.
@Deprecated
public abstract class LoggingContext extends ContextDataProvider {
  // Needed temporarily while old LoggingContext based implementations are migrated away from.
  private static final ScopedLoggingContext NO_OP_API = new NoOpScopedLoggingContext();

  @Override
  public ScopedLoggingContext getContextApiSingleton() {
    return NO_OP_API;
  }

  private static final class NoOpScopedLoggingContext extends ScopedLoggingContext
      implements LoggingContextCloseable {
    @Override
    public Builder newContext() {
      return new Builder() {
        @Override
        public LoggingContextCloseable install() {
          return NoOpScopedLoggingContext.this;
        }
      };
    }

    @Override
    public Builder newContext(ScopeType scopeType) {
      // Scopes unsupported in the old LoggingContext based implementations.
      return newContext();
    }

    @Override
    public void close() {}

    @Override
    public boolean addTags(Tags tags) {
      return false;
    }

    @Override
    public boolean applyLogLevelMap(LogLevelMap m) {
      return false;
    }
  }
}
