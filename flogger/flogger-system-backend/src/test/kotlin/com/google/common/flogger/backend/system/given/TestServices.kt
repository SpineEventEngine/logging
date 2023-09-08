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

package com.google.common.flogger.backend.system.given

import com.google.auto.service.AutoService
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.system.BackendFactory
import com.google.common.flogger.backend.system.Clock
import com.google.common.flogger.context.ContextDataProvider
import com.google.common.flogger.context.ScopedLoggingContext

/**
 * This file contains Java services that are used to test how
 * the default platform picks up the services in the runtime.
 */

/**
 * A stub Java [service][java.util.ServiceLoader] for [BackendFactory].
 */
@AutoService(BackendFactory::class)
class TestServices : BackendFactory() {

    override fun create(loggingClassName: String): LoggerBackend =
        throw UnsupportedOperationException()

    override fun toString(): String = this::class.simpleName!!
}

/**
 * A stub Java [service][java.util.ServiceLoader] for [ContextDataProvider].
 */
@AutoService(ContextDataProvider::class)
class TestContextDataProviderService : ContextDataProvider() {

    override fun getContextApiSingleton(): ScopedLoggingContext =
        throw UnsupportedOperationException()

    override fun toString(): String = this::class.simpleName!!
}

/**
 * A stub Java [service][java.util.ServiceLoader] for [Clock].
 */
@AutoService(Clock::class)
class TestClockService : Clock() {

    override fun getCurrentTimeNanos(): Long =
        throw UnsupportedOperationException()

    override fun toString(): String = this::class.simpleName!!
}
