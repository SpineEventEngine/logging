/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.logging

import io.spine.logging.LoggingFactory.loggingDomainOf
import kotlin.reflect.KClass

/**
 * Base class for fluent API loggers.
 *
 * A logger is a factory of fluent logging [API] instances,
 * which allow building log statements via method chaining.
 *
 * @param [API] The logging API provided by this logger.
 * @param [cls] The class which is going to perform the logging operations using this logger.
 * @see [LoggingApi]
 */
public abstract class Logger<API: LoggingApi<API>>(
    protected val cls: KClass<*>
) {
    /**
     * Returns a fluent logging [API] for the specified level of logging.
     *
     * If the specified level of logging is disabled at this point, the method
     * returns a "no-op" instance which silently ignores further calls to the logging [API].
     */
    public fun at(level: Level): API {
        val api = createApi(level)
        if (!api.isEnabled()) {
            return api
        }
        val loggingDomain = loggingDomainOf(cls)
        return if (loggingDomain.name.isEmpty()) {
            api
        } else {
            api.withLoggingDomain(loggingDomain)
        }
    }

    /**
     * Creates a new [API] instance with the given level.
     *
     * If a logger implementation determines that logging is definitely disabled
     * at this point, the implementation should return an instance of a class
     * extending [LoggingApi.NoOp] which would be a non-wildcard, fully specified, no-op
     * implementation of the [API] type.
     */
    protected abstract fun createApi(level: Level): API

    /*
     * IMPLEMENTATION NOTE
     *
     * The following methods are not implemented as extension functions in order to
     * preserve the calling site, which is supposed to be set by `createApi()` method
     * of the concrete logger implementation.
     *
     * Had we implemented these methods as extension functions, the calling site would
     * be `LoggerKt` class, which is not what we want.
     */

    /**
     * A convenience method for `at(Level.TRACE)`.
     */
    public fun atTrace(): API = at(Level.TRACE)

    /**
     * A convenience method for `at(Level.DEBUG)`.
     */
    public fun atDebug(): API = at(Level.DEBUG)

    /**
     * A convenience method for `at(Level.INFO)`.
     */
    public fun atInfo(): API = at(Level.INFO)

    /**
     * A convenience method for `at(Level.WARNING)`.
     */
    public fun atWarning(): API = at(Level.WARNING)

    /**
     * A convenience method for `at(Level.ERROR)`.
     */
    public fun atError(): API = at(Level.ERROR)
}
