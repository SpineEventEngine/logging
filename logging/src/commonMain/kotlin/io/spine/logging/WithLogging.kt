/*
 * Copyright 2025, TeamDev. All rights reserved.
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

/**
 * Provides [Logger] instance as a property.
 *
 * Implement this interface when logging is needed.
 *
 * Usage example:
 *
 * ```kotlin
 * import io.spine.logging.WithLogging
 *
 * class MyClass : WithLogging {
 *     fun doAction() {
 *         logger.atInfo().log { "Action is in progress." }
 *     }
 * }
 * ```
 *
 * ### Note for actual implementations
 *
 * Actual implementations are meant to take a logger from [LoggingFactory]:
 *
 * ```kotlin
 * import io.spine.logging.LoggingFactory.loggerFor
 *
 * public actual interface WithLogging {
 *     public actual val logger: Logger<*>
 *         get() = loggerFor(this::class)
 * }
 * ```
 *
 * Indeed, this interface could have a default implementation of [WithLogging.logger]
 * if default implementations for expected interfaces have been supported.
 * Take a look at [KT-20427](https://youtrack.jetbrains.com/issue/KT-20427/Allow-expect-declarations-with-a-default-implementation)
 * for details.
 *
 * As for now, providing a default implementation for a property makes it
 * impossible to customize accessing of a logger in target implementations.
 */
public expect interface WithLogging {

    /**
     * Returns the logger created for this class.
     */
    public open val logger: Logger<*>
}
