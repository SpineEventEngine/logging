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

package io.spine.logging.backend.system.given

import com.google.auto.service.AutoService
import io.spine.logging.jvm.backend.LoggerBackend
import io.spine.logging.jvm.backend.BackendFactory
import io.spine.logging.jvm.backend.Clock
import io.spine.logging.jvm.context.ContextDataProvider
import io.spine.logging.jvm.context.ScopedLoggingContext

/**
 * This file contains Java services that are used to test how
 * the default platform picks up the services in the runtime.
 */
@Suppress("unused") // Makes file-level doc renderable.
private val about = ""

/**
 * A stub service for [BackendFactory] that can be loaded
 * by Java's [ServiceLoader][java.util.ServiceLoader].
 */
@AutoService(BackendFactory::class)
internal class StubBackendFactoryService : BackendFactory() {

    override fun create(loggingClassName: String): LoggerBackend =
        throw UnsupportedOperationException()

    override fun toString(): String = this::class.qualifiedName!!
}

/**
 * A stub service for [ContextDataProvider] that can be loaded
 * by Java's [ServiceLoader][java.util.ServiceLoader].
 */
@AutoService(ContextDataProvider::class)
internal class StubContextDataProviderService : ContextDataProvider() {

    override fun getContextApiSingleton(): ScopedLoggingContext =
        throw UnsupportedOperationException()

    override fun toString(): String = this::class.qualifiedName!!
}

/**
 * A stub service for [Clock] that can be loaded
 * by Java's [ServiceLoader][java.util.ServiceLoader].
 */
@AutoService(Clock::class)
internal class StubClockService : Clock() {

    override fun getCurrentTimeNanos(): Long =
        throw UnsupportedOperationException()

    override fun toString(): String = this::class.qualifiedName!!
}
