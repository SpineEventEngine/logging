/*
 * Copyright 2014, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

import static io.spine.logging.flogger.FloggerLogContext.Key.LOG_AT_MOST_EVERY;
import static io.spine.logging.flogger.util.Checks.checkArgument;
import static io.spine.logging.flogger.util.Checks.checkNotNull;
import static java.lang.Math.max;

import io.spine.logging.flogger.backend.LogData;
import io.spine.logging.flogger.backend.Metadata;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Rate limiter to support {@code atMostEvery(N, units)} functionality.
 *
 * <p>Instances of this class are created for each unique {@link LogSiteKey} for which rate limiting
 * via the {@code LOG_AT_MOST_EVERY} metadata key is required. This class implements {@code
 * RateLimitStatus} as a mechanism for resetting the rate limiter state.
 *
 * <p>Instances of this class are thread safe.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/DurationRateLimiter.java">
 *     Original Java code of Google Flogger</a>
 */
final class DurationRateLimiter extends RateLimitStatus {
  private static final LogSiteMap<DurationRateLimiter> map =
      new LogSiteMap<DurationRateLimiter>() {
        @Override
        protected DurationRateLimiter initialValue() {
          return new DurationRateLimiter();
        }
      };

  /**
   * Creates a period for rate limiting for the specified duration. This is invoked by the {@link
   * FloggerLogContext#atMostEvery(int, TimeUnit)} method to create a metadata value.
   */
  static RateLimitPeriod newRateLimitPeriod(int n, TimeUnit unit) {
    // We could cache commonly used values here if we wanted.
    return new RateLimitPeriod(n, unit);
  }

  /**
   * Returns whether the log site should log based on the value of the {@code LOG_AT_MOST_EVERY}
   * metadata value and the current log site timestamp.
   */
  @Nullable
  static RateLimitStatus check(Metadata metadata, LogSiteKey logSiteKey, long timestampNanos) {
    RateLimitPeriod rateLimitPeriod = metadata.findValue(LOG_AT_MOST_EVERY);
    if (rateLimitPeriod == null) {
      // Without rate limiter specific metadata, this limiter has no effect.
      return null;
    }
    return map.get(logSiteKey, metadata).checkLastTimestamp(timestampNanos, rateLimitPeriod);
  }

  /**
   * Immutable metadata for rate limiting based on a fixed count. This corresponds to the
   * LOG_AT_MOST_EVERY metadata key in {@link LogData}. Unlike the metadata for {@code every(N)}, we
   * need to use a wrapper class here to preserve the time unit information.
   */
  static final class RateLimitPeriod {
    private final int n;
    private final TimeUnit unit;

    private RateLimitPeriod(int n, TimeUnit unit) {
      // This code will work with a zero length time period, but it's nonsensical to try.
      if (n <= 0) {
        throw new IllegalArgumentException("time period must be positive: " + n);
      }
      this.n = n;
      this.unit = checkNotNull(unit, "time unit");
    }

    private long toNanos() {
      // Since nanoseconds are the smallest level of precision a TimeUnit can express, we are
      // guaranteed that "unit.toNanos(n) >= n > 0". This is important for correctness (see comment
      // in checkLastTimestamp()) because it ensures the new timestamp that indicates when logging
      // should occur always differs from the previous timestamp.
      return unit.toNanos(n);
    }

    @Override
    public String toString() {
      return n + " " + unit;
    }

    @Override
    public int hashCode() {
      // Rough and ready. We don't expect this be be needed much at all.
      return (n * 37) ^ unit.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof RateLimitPeriod) {
        RateLimitPeriod that = (RateLimitPeriod) obj;
        return this.n == that.n && this.unit == that.unit;
      }
      return false;
    }
  }

  private final AtomicLong lastTimestampNanos = new AtomicLong(-1L);

  // Visible for testing.
  DurationRateLimiter() {}

  /**
   * Checks whether the current time stamp is after the rate limiting period and if so, updates the
   * time stamp and returns true. This is invoked during post-processing if a rate limiting duration
   * was set via {@link FloggerLoggingApi#atMostEvery(int, TimeUnit)}.
   */
  // Visible for testing.
  RateLimitStatus checkLastTimestamp(long timestampNanos, RateLimitPeriod period) {
    checkArgument(timestampNanos >= 0, "timestamp cannot be negative");
    // If this is negative, we are in the pending state and will return "allow" until we are reset.
    // The value held here is updated to be the most recent negated timestamp, and is negated again
    // (making it positive and setting us into the rate limiting state) when we are reset.
    long lastNanos = lastTimestampNanos.get();
    if (lastNanos >= 0) {
      long deadlineNanos = lastNanos + period.toNanos();
      // Check for negative deadline to avoid overflow for ridiculous durations. Assume overflow
      // always means "no logging".
      if (deadlineNanos < 0 || timestampNanos < deadlineNanos) {
        return DISALLOW;
      }
    }
    // When logging is triggered, negate the timestamp to move us into the "pending" state and
    // return our reset status.
    // We don't want to race with the reset function (which may have already set a new timestamp).
    lastTimestampNanos.compareAndSet(lastNanos, -timestampNanos);
    return this;
  }

  // Reset function called to move the limiter out of the "pending" state. We do this by negating
  // the timestamp (which was already negated when we entered the pending state, so we restore it
  // to a positive value which moves us back into the "limiting" state).
  @Override
  public void reset() {
    // Only one thread at a time can reset a rate limiter, so this can be unconditional. We should
    // only be able to get here if the timestamp was set to a negative value above. However use
    // max() to make sure we always move out of the pending state.
    lastTimestampNanos.set(max(-lastTimestampNanos.get(), 0));
  }
}
