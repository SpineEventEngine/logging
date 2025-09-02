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

package io.spine.logging.backend

/**
 * A clock to return walltime timestamps for log statements.
 *
 * This is implemented as an abstract class (rather than an interface) to reduce
 * the risk of breaking existing implementations if the API changes.
 *
 * ### Essential Implementation Restrictions
 *
 * Any implementation of this API **MUST** follow the rules listed below to avoid any risk of
 * re-entrant code calling during logger initialization. Failure to do so risks creating complex,
 * hard to debug, issues with Flogger configuration.
 *
 * 1. Implementations **MUST NOT** attempt any logging in static methods or constructors.
 * 2. Implementations **MUST NOT** statically depend on any unknown code.
 * 3. Implementations **MUST NOT** depend on any unknown code in constructors.
 *
 * Note that logging and calling arbitrary unknown code (which might log) are permitted inside
 * the instance methods of this API, since they are not called during platform initialization. The
 * easiest way to achieve this is to simply avoid having any non-trivial static fields or any
 * instance fields at all in the implementation.
 *
 * While this sounds onerous it's not difficult to achieve because this API is a singleton, and
 * can delay any actual work until its methods are called. For example if any additional state is
 * required in the implementation, it can be held via a "lazy holder" to defer initialization.
 *
 * ### This is a service type
 *
 * This type is considered a *service type*, and implementations may be loaded using
 * a platform-specific mechanism from the available classpath.
 *
 * Under JVM, it could be done via the [java.util.ServiceLoader] API provided
 * the proper service metadata is included in the jar file containing the implementation.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/Clock.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class Clock {

    /**
     * Returns the current time from the epoch (`00:00 1st Jan, 1970`) with nanosecond
     * granularity, though not necessarily nanosecond precision.
     *
     * This clock measures UTC and is not required to handle leap seconds.
     */
    public abstract fun getCurrentTimeNanos(): Long
}
