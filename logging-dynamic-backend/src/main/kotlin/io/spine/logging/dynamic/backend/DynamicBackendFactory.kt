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

package io.spine.logging.dynamic.backend

import com.google.auto.service.AutoService
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.system.BackendFactory
import com.google.common.flogger.backend.system.SimpleBackendFactory

@AutoService(BackendFactory::class)
public class DynamicBackendFactoryService : BackendFactory() {

    override fun create(loggingClassName: String): LoggerBackend =
        DynamicBackendFactory.create(loggingClassName)

    override fun toString(): String = DynamicBackendFactory.toString()
}

public object DynamicBackendFactory : BackendFactory() {

    private val simpleBackends = SimpleBackendFactory.getInstance()
    private var customBackends: BackendProvider<*>? = null

    public fun from(backends: BackendProvider<*>) {
        customBackends = backends
    }

    public fun useDefaultBackend() {
        customBackends = null
    }

    override fun create(loggingClassName: String): LoggerBackend =
        customBackends?.create(loggingClassName) ?: simpleBackends.create(loggingClassName)

    override fun toString(): String = "Dynamic Backend Factory"
}

public fun interface BackendProvider<out T : LoggerBackend> {

    public fun create(loggingClassName: String): T
}
