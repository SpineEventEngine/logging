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

package io.spine.logging.backend.probe

import io.spine.logging.flogger.backend.LoggerBackend
import io.spine.logging.flogger.backend.BackendFactory
import io.spine.logging.backend.jul.JulBackendFactory

/**
 * A factory that delegates backends creation to another factory,
 * and allows changing of the underlying factory in runtime.
 *
 * In general, the logging facade doesn't provide a mechanism for changing
 * the underlying backend factory in runtime. It shouldn't make any sense
 * in real code. But for tests, it can be handy.
 *
 * Different tests may need different backend stubs to perform their assertions.
 * More importantly, they need those stubs [typed][TypedBackendFactory] to access
 * their “enriched” API.
 *
 * This factory uses [JulBackendFactory] as a fall-back option,
 * when no custom [delegate] is specified.
 *
 * The type is public because it is used in a public inline method.
 */
public object DynamicBackendFactory : BackendFactory() {

    private val julBackends = JulBackendFactory()
    private var delegate: TypedBackendFactory<*>? = null

    /**
     * Makes the factory delegate backends creation to the given [factory].
     */
    public fun delegate(factory: TypedBackendFactory<*>) {
        delegate = factory
    }

    /**
     * Resets the custom delegate, and makes the factory delegate
     * backends creation to the default [JulBackendFactory].
     */
    public fun reset() {
        delegate = null
    }

    /**
     * Creates a new backend using the configured [delegate], if any.
     *
     * Otherwise, uses [JulBackendFactory] to create a backend.
     */
    override fun create(loggingClassName: String): LoggerBackend =
        delegate?.create(loggingClassName) ?: julBackends.create(loggingClassName)

    /**
     * Returns a fully-qualified name of this class.
     */
    override fun toString(): String = javaClass.name
}
