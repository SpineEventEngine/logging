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

package io.spine.logging.jvm.backend;

/**
 * A clock to return walltime timestamps for log statements. This is implemented as an abstract
 * class (rather than an interface) to reduce to risk of breaking existing implementations if the
 * API changes.
 *
 * <h2>Essential Implementation Restrictions</h2>
 *
 * Any implementation of this API <em>MUST</em> follow the rules listed below to avoid any risk of
 * re-entrant code calling during logger initialization. Failure to do so risks creating complex,
 * hard to debug, issues with Flogger configuration.
 *
 * <ol>
 *   <li>Implementations <em>MUST NOT</em> attempt any logging in static methods or constructors.
 *   <li>Implementations <em>MUST NOT</em> statically depend on any unknown code.
 *   <li>Implementations <em>MUST NOT</em> depend on any unknown code in constructors.
 * </ol>
 *
 * <p>Note that logging and calling arbitrary unknown code (which might log) are permitted inside
 * the instance methods of this API, since they are not called during platform initialization. The
 * easiest way to achieve this is to simply avoid having any non-trivial static fields or any
 * instance fields at all in the implementation.
 *
 * <p>While this sounds onerous it's not difficult to achieve because this API is a singleton, and
 * can delay any actual work until its methods are called. For example if any additional state is
 * required in the implementation, it can be held via a "lazy holder" to defer initialization.
 *
 * <h2>This is a service type</h2>
 *
 * <p>This type is considered a <i>service type</i> and implemenations may be loaded from the
 * classpath via {@link java.util.ServiceLoader} provided the proper service metadata is included in
 * the jar file containing the implementation. When creating an implementation of this class, you
 * can provide serivce metadata (and thereby allow users to get your implementation just by
 * including your jar file) by either manually including a {@code
 * META-INF/services/io.spine.logging.jvm.backend.Clock} file containing the name of
 * your implementation class or by annotating your implementation class using <a
 * href="https://github.com/google/auto/tree/master/service">{@code @AutoService(Clock.class)}</a>.
 * See the documentation of both {@link java.util.ServiceLoader} and {@code DefaultPlatform} for
 * more information.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/Clock.java">
 *     Original Java code of Google Flogger</a>
 */
public abstract class Clock {
  /**
   * Returns the current time from the epoch (00:00 1st Jan, 1970) with nanosecond granularity,
   * though not necessarily nanosecond precision. This clock measures UTC and is not required to
   * handle leap seconds.
   */
  public abstract long getCurrentTimeNanos();
}
