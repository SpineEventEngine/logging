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

/**
 * A typed [BackendFactory][com.google.common.flogger.backend.system.BackendFactory].
 *
 * This interface can't extend the original factory because it is an abstract class.
 *
 * Knowing a type of the returned backend is useful when it is used in a pair with
 * [DynamicBackendFactory] to perform test assertions. With a captured type,
 * we don't lose the API of stub backends. For example, allowing to retrieve
 * the remembered log data from `FakeLoggerBackend`.
 *
 *  The type is public because it is used in a public inline method.
 *
 * @param T type of the returned backends
 */
public fun interface TypedBackendFactory<out T : LoggerBackend> {

    /**
     * Creates a logger backend for the given class name.
     */
    public fun create(loggingClassName: String): T
}
