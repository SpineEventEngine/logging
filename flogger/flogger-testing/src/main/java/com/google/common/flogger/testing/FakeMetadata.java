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

package com.google.common.flogger.testing;

import static io.spine.logging.flogger.util.Checks.checkNotNull;

import io.spine.logging.flogger.FloggerMetadataKey;
import io.spine.logging.flogger.backend.Metadata;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A mutable fake {@link Metadata} implementation to help test logging backends,
 * and other log handling code.
 *
 * @see <a href="http://rb.gy/h75mb">Original Java code of Google Flogger</a>
 */
public final class FakeMetadata extends Metadata {

  private static final class KeyValuePair<T> {
    private final FloggerMetadataKey<T> key;
    private final T value;
    private KeyValuePair(FloggerMetadataKey<T> key, T value) {
      this.key = checkNotNull(key, "key");
      this.value = checkNotNull(value, "value");
    }
  }

  private final List<KeyValuePair<?>> entries = new ArrayList<>();

  @CanIgnoreReturnValue
  public <T> FakeMetadata add(FloggerMetadataKey<T> key, T value) {
    entries.add(new KeyValuePair<>(key, value));
    return this;
  }

  @Override public int size() {
    return entries.size();
  }

  @Override public FloggerMetadataKey<?> getKey(int n) {
    return entries.get(n).key;
  }
  @Override public Object getValue(int n) {
    return entries.get(n).value;
  }

  @Override
  @Nullable
  public <T> T findValue(FloggerMetadataKey<T> key) {
    for (var entry : entries) {
      if (entry.key.equals(key)) {
        return key.cast(entry.value);
      }
    }
    return null;
  }
}

