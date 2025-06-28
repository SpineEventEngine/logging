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

package io.spine.logging.jvm.backend

/**
 * An API to create logger backends for a given class name.
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
 * the instance methods of this API, since they are not called during platform initialization.
 * The easiest way to achieve this is to simply avoid having any non-trivial static fields or
 * any instance fields at all in the implementation.
 *
 * While this sounds onerous it is not difficult to achieve because this API is a singleton, and
 * can delay any actual work until its methods are called. For example if any additional state
 * is required in the implementation, it can be held via a "lazy holder" to defer initialization.
 *
 * ### This is a service type
 *
 * This type is considered a *service type* and implementations may be loaded from the
 * classpath via [java.util.ServiceLoader] provided the proper service metadata is included in
 * the jar file containing the implementation. When creating an implementation of this class,
 * you can provide service metadata (and thereby allow users to get your implementation just by
 * including your jar file) by either manually including a
 * `META-INF/services/io.spine.logging.jvm.backend.BackendFactory` file containing the name of
 * your implementation class or by annotating your implementation class using
 * [AutoService(BackendFactory::class)](https://github.com/google/auto/tree/master/service).
 * See the documentation of both [java.util.ServiceLoader] and `DefaultPlatform`
 * for more information.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/system/BackendFactory.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public abstract class BackendFactory {

    /**
     * Creates a logger backend of the given class name for use by a Fluent Logger. Note that
     * the returned backend need not be unique; one backend could be used by multiple loggers.
     * The given class name must be in the normal dot-separated form (e.g., "com.example.Foo$Bar")
     * rather than the internal binary format "com/example/Foo$Bar").
     *
     * @param loggingClass The fully-qualified name of the Java class to which the logger is
     *        associated. The logger name is derived from this string in a backend-specific way.
     */
    public abstract fun create(loggingClass: String): LoggerBackend
}
