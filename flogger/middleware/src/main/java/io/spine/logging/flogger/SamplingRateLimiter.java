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

package io.spine.logging.flogger;

import io.spine.logging.flogger.backend.Metadata;
import org.jspecify.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static io.spine.logging.flogger.LogContext.Key.LOG_SAMPLE_EVERY_N;

/**
 * Rate limiter to support {@code onAverageEvery(N)} functionality.
 *
 * <p>Instances of this class are created for each unique {@link LogSiteKey} for which rate limiting
 * via the {@code LOG_SAMPLE_EVERY_N} metadata key is required. This class implements {@code
 * RateLimitStatus} as a mechanism for resetting its own state.
 *
 * <p>This class is thread safe.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/SamplingRateLimiter.java">
 *     Original Java code of Google Flogger</a>
 */
final class SamplingRateLimiter extends RateLimitStatus {
  private static final LogSiteMap<SamplingRateLimiter> map =
      new LogSiteMap<SamplingRateLimiter>() {
        @Override
        protected SamplingRateLimiter initialValue() {
          return new SamplingRateLimiter();
        }
      };

  @Nullable
  static RateLimitStatus check(Metadata metadata, LogSiteKey logSiteKey) {
    Integer rateLimitCount = metadata.findValue(LOG_SAMPLE_EVERY_N);
    if (rateLimitCount == null || rateLimitCount <= 0) {
      // Without valid rate limiter specific metadata, this limiter has no effect.
      return null;
    }
    return map.get(logSiteKey, metadata).sampleOneIn(rateLimitCount);
  }

  // Even though Random is synchonized, we have to put it in a ThreadLocal to avoid thread
  // contention. We cannot use ThreadLocalRandom (yet) due to JDK level.
  private static final ThreadLocal<Random> random = new ThreadLocal<Random>() {
    @Override
    protected Random initialValue() {
      return new Random();
    }
  };

  // Visible for testing.
  final AtomicInteger pendingCount = new AtomicInteger();

  // Visible for testing.
  SamplingRateLimiter() {}

  RateLimitStatus sampleOneIn(int rateLimitCount) {
    // Always "roll the dice" and adjust the count if necessary (even if we were already
    // pending). This means that in the long run we will account for every time we roll a
    // zero and the number of logs will end up statistically close to 1-in-N (even if at
    // times they can be "bursty" due to the action of other rate limiting mechanisms).
    int pending;
    if (random.get().nextInt(rateLimitCount) == 0) {
      pending = pendingCount.incrementAndGet();
    } else {
      pending = pendingCount.get();
    }
    return pending > 0 ? this : DISALLOW;
  }

  @Override
  public void reset() {
    pendingCount.decrementAndGet();
  }
}
