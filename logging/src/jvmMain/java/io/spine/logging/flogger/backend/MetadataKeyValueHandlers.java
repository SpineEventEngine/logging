/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.flogger.backend;

import io.spine.logging.flogger.MetadataKey;
import io.spine.logging.flogger.MetadataKey.KeyValueHandler;
import io.spine.logging.flogger.backend.MetadataHandler.RepeatedValueHandler;
import io.spine.logging.flogger.backend.MetadataHandler.ValueHandler;
import java.util.Iterator;
import java.util.Set;

/**
 * A helper class providing the default callbacks and handlers for processing metadata as key/value
 * pairs. It is expected that most text-based logger backends will format unknown metadata using the
 * handlers from this class.
 */
public final class MetadataKeyValueHandlers {
  private static final ValueHandler<Object, KeyValueHandler> EMIT_METADATA =
      new ValueHandler<Object, KeyValueHandler>() {
        @Override
        public void handle(MetadataKey<Object> key, Object value, KeyValueHandler kvf) {
          key.safeEmit(value, kvf);
        }
      };

  private static final RepeatedValueHandler<Object, KeyValueHandler> EMIT_REPEATED_METADATA =
      new RepeatedValueHandler<Object, KeyValueHandler>() {
        @Override
        public void handle(MetadataKey<Object> key, Iterator<Object> value, KeyValueHandler kvf) {
          key.safeEmitRepeated(value, kvf);
        }
      };

  /** Returns a singleton value handler which dispatches metadata to a {@link KeyValueHandler}. */
  public static ValueHandler<Object, KeyValueHandler> getDefaultValueHandler() {
    return EMIT_METADATA;
  }

  /** Returns a singleton value handler which dispatches metadata to a {@link KeyValueHandler}. */
  public static RepeatedValueHandler<Object, KeyValueHandler> getDefaultRepeatedValueHandler() {
    return EMIT_REPEATED_METADATA;
  }

  /**
   * Returns a new {@link MetadataHandler.Builder} which handles all non-ignored metadata keys by
   * dispatching their values to the key itself. This is convenient for generic metadata processing
   * when used in conjunction with something like {@link KeyValueFormatter}.
   *
   * <p>The returned builder can be built immediately or customized further to handler some keys
   * specially (e.g. allowing keys/values to modify logging behaviour).
   *
   * @return a builder configured with the default key/value handlers and ignored keys.
   */
  public static MetadataHandler.Builder<KeyValueHandler> getDefaultBuilder(
      Set<MetadataKey<?>> ignored) {
    return MetadataHandler.builder(getDefaultValueHandler())
        .setDefaultRepeatedHandler(getDefaultRepeatedValueHandler())
        .ignoring(ignored);
  }

  /**
   * Returns a new {@link MetadataHandler} which handles all non-ignored metadata keys by
   * dispatching their values to the key itself. This is convenient for generic metadata processing
   * when used in conjunction with something like {@link KeyValueFormatter}.
   *
   * @return a handler configured with the default key/value handlers and ignored keys.
   */
  public static MetadataHandler<KeyValueHandler> getDefaultHandler(Set<MetadataKey<?>> ignored) {
    return getDefaultBuilder(ignored).build();
  }

  private MetadataKeyValueHandlers() {}
}
