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

package io.spine.logging

/**
 * Represents the location of a single log statement.
 *
 * This type is used to identify a particular log statement and provide
 * a linkage between the statement itself and its metadata.
 *
 * For example, the logging [facade][LoggingApi] allows configuring of
 * a logging statement to be emitted only if a specific condition is satisfied.
 * Consider the [LoggingApi.atMostEvery] method, which configures a log statement
 * to perform actual logging no often than once per the specified period when
 * called multiple times. To achieve this, the facade needs to track
 * previous invocations, and this information is part of metadata that is stored
 * for each statement.
 *
 * Usually, this type if filled from a stack trace until it is injected
 * [manually][LoggingApi.withInjectedLogSite], or the used backend provides
 * its own mechanism to determine a log site.
 */
public interface LogSite {

    /**
     * Full name of the class containing the log statement.
     */
    public val className: String

    /**
     * Name of the method containing the log statement.
     */
    public val methodName: String

    /**
     * Line number of the log statement.
     */
    public val lineNumber: Int
}
