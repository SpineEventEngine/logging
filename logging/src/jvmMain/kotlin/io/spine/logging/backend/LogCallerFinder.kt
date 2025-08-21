/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.jvm.AbstractLogger
import io.spine.logging.LogSite
import kotlin.reflect.KClass

/**
 * API for determining the logging class and log statement sites,
 * return from [Platform.getCallerFinder].
 *
 * These classes are immutable and thread-safe.
 *
 * This functionality is not provided directly by the `Platform` API because doing so would
 * require several additional levels to be added to the stack before the implementation was
 * reached. This is problematic for Android, which has only limited stack analysis. By allowing
 * callers to resolve the implementation early and then call an instance directly (this is not
 * an interface), we reduce the number of elements in the stack before the caller is found.
 *
 * ## Essential Implementation Restrictions
 *
 * Any implementation of this API *MUST* follow the rules listed below to avoid any risk of
 * re-entrant code calling during logger initialization. Failure to do so risks creating complex,
 * hard to debug, issues with Flogger configuration.
 *
 * 1. Implementations *MUST NOT* attempt any logging in static methods or constructors.
 * 2. Implementations *MUST NOT* statically depend on any unknown code.
 * 3. Implementations *MUST NOT* depend on any unknown code in constructors.
 *
 * Note that logging and calling arbitrary unknown code (which might log) are permitted inside
 * the instance methods of this API, since they are not called during platform initialization.
 * The
 * easiest way to achieve this is to simply avoid having any non-trivial static fields or any
 * instance fields at all in the implementation.
 *
 * While this sounds onerous it is not difficult to achieve because this API is a singleton, and
 * can delay any actual work until its methods are called. For example, if any additional state is
 * required in the implementation, it can be held via a "lazy holder" to defer initialization.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/Platform.java#L160">
 *   Original Java code of Google Flogger</a> for historical context.
 */
@Immutable
@ThreadSafe
public abstract class LogCallerFinder {

    /**
     * Returns the name of the immediate caller of the given logger class.
     *
     * This is useful when determining the class name with which to create a logger backend.
     *
     * @param loggerClass The class containing the log() methods whose caller we need to find.
     * @return The name of the class called the specified logger.
     * @throws IllegalStateException If there was no caller of the specified logged passed
     *         on the stack (which may occur if the logger class was invoked directly by JNI).
     */
    public abstract fun findLoggingClass(loggerClass: KClass<out AbstractLogger<*>>): String

    /**
     * Returns a LogSite found from the current stack trace for the caller of the log() method
     * on the given logging class.
     *
     * @param loggerApi The class containing the log() methods whose caller we need to find.
     * @param stackFramesToSkip The number of method calls which exist on the stack between the
     *        `log()` method, and the point at which this method is invoked.
     * @return A log site inferred from the stack, or [io.spine.logging.LogSite.Invalid] if no log site
     *         can be determined.
     */
    public abstract fun findLogSite(loggerApi: KClass<*>, stackFramesToSkip: Int): LogSite
}
