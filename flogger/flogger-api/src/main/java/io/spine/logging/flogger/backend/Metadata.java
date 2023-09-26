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

package io.spine.logging.flogger.backend;

import io.spine.logging.flogger.MetadataKey;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A sequence of metadata key/value pairs which can be associated to a log statement, either
 * directly via methods in the fluent API, of as part of a scoped logging context.
 *
 * <p>Metadata keys can "single valued" or "repeating" based on {@link MetadataKey#canRepeat}, but
 * it is permitted for a {@code Metadata} implementation to retain multiple single valued keys, and
 * in that situation the key at the largest index is the one which should be used.
 *
 * <p>Multiple {@code Metadata} instances can be merged, in order, to provide a final sequence for
 * a log statement. When {@code Metadata} instance are merged, the result is just the concatenation
 * of the sequence of key/value pairs, and this is what results in the potential for mutliple single
 * valued keys to exist.
 *
 * <p>If the value of a single valued key is required, the {@link #findValue(MetadataKey)} method
 * should be used to look it up. For all other metadata processing, a {@link MetadataProcessor}
 * should be created to ensure that scope and log site metadata can be merged correctly.
 */
public abstract class Metadata {

  /** Returns an immutable {@link Metadata} that has no items. */
  public static Metadata empty() {
    return Empty.INSTANCE;
  }

  // This is a static nested class as opposed to an anonymous class assigned to a constant field in
  // order to decouple it's classload when Metadata is loaded. Android users are particularly
  // careful about unnecessary class loading, and we've used similar mechanisms in Guava (see
  // CharMatchers)
  private static final class Empty extends Metadata {
    static final Empty INSTANCE = new Empty();

    @Override
    public int size() {
      return 0;
    }

    @Override
    public MetadataKey<?> getKey(int n) {
      throw new IndexOutOfBoundsException("cannot read from empty metadata");
    }

    @Override
    public Object getValue(int n) {
      throw new IndexOutOfBoundsException("cannot read from empty metadata");
    }

    @Override
    @Nullable
    public <T> T findValue(MetadataKey<T> key) {
      return null;
    }
  }

  /** Returns the number of key/value pairs for this instance. */
  public abstract int size();

  /**
   * Returns the key for the Nth piece of metadata.
   *
   * @throws IndexOutOfBoundsException if either {@code n < 0} or {n >= getCount()}.
   */
  public abstract MetadataKey<?> getKey(int n);

  /**
   * Returns the non-null value for the Nth piece of metadata.
   *
   * @throws IndexOutOfBoundsException if either {@code n < 0} or {n >= getCount()}.
   */
  public abstract Object getValue(int n);

  /**
   * Returns the first value for the given single valued metadata key, or null if it does not exist.
   *
   * @throws NullPointerException if {@code key} is {@code null}.
   */
  // TODO(dbeaumont): Make this throw an exception for repeated keys.
  @Nullable
  public abstract <T> T findValue(MetadataKey<T> key);
}
