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

import io.spine.logging.LoggingFactory.loggingDomainOf
import kotlin.reflect.KClass

public abstract class Logger<API: LoggingApi<API>>(
    protected val cls: KClass<*>
) {
    /**
     * Creates an [API] for the given level of logging.
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
            @Suppress("UNCHECKED_CAST") // Safe to cast since delegates to the same interface.
            WithLoggingDomain(loggingDomain, api) as API
        }
    }

    protected abstract fun createApi(level: Level): API

    public fun atDebug(): API = at(Level.DEBUG)
    public fun atInfo(): API = at(Level.INFO)
    public fun atWarning(): API = at(Level.WARNING)
    public fun atError(): API = at(Level.ERROR)
}

/**
 * Wraps given [API] to prepend [LoggingDomain.messagePrefix] to
 * the [messages][log] of the [delegate].
 */
private class WithLoggingDomain<API: LoggingApi<API>>(
    private val loggingDomain: LoggingDomain,
    private val delegate: API
): LoggingApi<API> by delegate {

    override fun log(message: () -> String) = delegate.log {
        "${loggingDomain.messagePrefix}${message()}"
    }
}