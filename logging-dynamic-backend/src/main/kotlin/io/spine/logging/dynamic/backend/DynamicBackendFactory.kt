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

import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.system.BackendFactory
import com.google.common.flogger.backend.system.SimpleBackendFactory

/**
 * A factory that delegates backends creation to another factory,
 * and allows changing of the underlying factory in runtime.
 *
 * In general, it is prohibited to change the underlying backend factory
 * in runtime as it shouldn't make any sense in real code.
 *
 * But different tests may need different backend stubs to perform their assertions.
 * More importantly, they need those stubs [typed][TypedBackendFactory] to access
 * their “enriched” API. So, having a factory that can change the underlying backend
 * in runtime is quite useful for logging tests.
 *
 * Making this factory an `object` eases access to it from [withBackend] method.
 * Also, backend factories are meant to be used as singletons.
 *
 * ## Default delegate
 *
 * Backend factories should be operational from the very beginning.
 * Since this factory always exists (remember, it is an object), it is impossible
 * to provide the default delegate during initialization. Thus, this factory
 * always rolls back to [SimpleBackendFactory] if no factory is [provided][delegate].
 */
public object DynamicBackendFactory : BackendFactory() {

    private val simpleBackends = SimpleBackendFactory.getInstance()
    private var delegate: TypedBackendFactory<*>? = null

    /**
     * Makes the factory delegate backends creation to the given [factory].
     */
    public fun delegate(factory: TypedBackendFactory<*>) {
        delegate = factory
    }

    /**
     * Resets the custom delegate, and makes the factory delegate
     * backends creation to the default [SimpleBackendFactory].
     */
    public fun reset() {
        delegate = null
    }

    override fun create(loggingClassName: String): LoggerBackend =
        delegate?.create(loggingClassName) ?: simpleBackends.create(loggingClassName)

    override fun toString(): String = "Dynamic Backend Factory"
}
