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

import kotlin.reflect.KClass

/**
 * A factory for [Logger] instances.
 */
public expect object LoggingFactory {

    /**
     * Obtains the logger for the given class.
     *
     * Implementation should provide the same logger instance for the same class.
     */
    public fun <API: LoggingApi<API>> loggerFor(cls: KClass<*>): Logger<API>

    /**
     * Obtains a logging domain for the given class.
     *
     * If the domain is not specified, returns [LoggingDomain.noOp].
     */
    public fun loggingDomainOf(cls: KClass<*>): LoggingDomain
}

/**
 * Obtains a name of a logger to be used for this class.
 *
 * For a fully-qualified class, its name will be used.
 * Otherwise, if a class has a simple name, it will be used.
 * If a class does not have a simple name, the string representation
 * of the class will be returned.
 */
internal fun KClass<*>.toLoggerName(): String = qualifiedName?:simpleName?:toString()
