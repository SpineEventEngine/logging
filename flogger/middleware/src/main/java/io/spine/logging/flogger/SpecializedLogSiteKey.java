/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.flogger;

import static io.spine.logging.flogger.util.Checks.checkNotNull;

import org.jspecify.annotations.Nullable;

/**
 * Used by Scope/LogSiteMap and in response to "per()" or "perUnique()" (which is an implicitly
 * unbounded scope. This should avoid it needing to be made public assuming it's in the same
 * package.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/SpecializedLogSiteKey.java">
 *     Original Java code of Google Flogger</a>
 */
final class SpecializedLogSiteKey implements LogSiteKey {
  static LogSiteKey of(LogSiteKey key, Object qualifier) {
    return new SpecializedLogSiteKey(key, qualifier);
  }

  private final LogSiteKey delegate;
  private final Object qualifier;

  private SpecializedLogSiteKey(LogSiteKey key, Object qualifier) {
    this.delegate = checkNotNull(key, "log site key");
    this.qualifier = checkNotNull(qualifier, "log site qualifier");
  }

  // Equals is dependent on the order in which specialization occurred, even though conceptually it
  // needn't be.
  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof SpecializedLogSiteKey)) {
      return false;
    }
    SpecializedLogSiteKey other = (SpecializedLogSiteKey) obj;
    return delegate.equals(other.delegate) && qualifier.equals(other.qualifier);
  }

  @Override
  public int hashCode() {
    // Use XOR (which is symmetric) so hash codes are not dependent on specialization order.
    return delegate.hashCode() ^ qualifier.hashCode();
  }

  @Override
  public String toString() {
    return "SpecializedLogSiteKey{ delegate='" + delegate + "', qualifier='" + qualifier + "' }";
  }
}
