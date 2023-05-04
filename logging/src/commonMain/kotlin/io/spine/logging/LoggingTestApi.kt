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

public abstract class LoggingTestApi<API : LoggingApi<API>>(
    private val delegate: LoggingApi<API>
) : LoggingApi<API> {

    public var cause: Throwable? = null
    public var parameterlessLogMethodCalled: Boolean = false
    public var logMethodCalled: Boolean = false
    public var message: String? = null

    override fun withCause(cause: Throwable): API {
        this.cause = cause
        delegate.withCause(cause)
        return self()
    }

    protected abstract fun self(): API

    override fun isEnabled(): Boolean = delegate.isEnabled()

    override fun log() {
        parameterlessLogMethodCalled = true
        delegate.log()
    }

    override fun log(message: () -> String) {
        logMethodCalled = true
        delegate.log(message)
    }

    public companion object {
        @JvmStatic
        public var enabled: Boolean = false
    }
}
