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

import io.spine.logging.LoggingFactory.loggerFor

/**
 * Provides [Logger] instance as a property.
 *
 * Implement this interface when logging is needed.
 *
 * Usage example:
 *
 * ```kotlin
 * class MyClass : WithLogging {
 *     fun doAction() {
 *         logger.atInfo().log { "Action is in progress." }
 *
 *         // Or, via the level shortcut property:
 *
 *         atInfo.log { "Action is in progress." }
 *     }
 * }
 * ```
 */
public interface WithLogging {

    /**
     * Returns the logger created for this class.
     */
    public val logger: Logger
        get() = loggerFor(this::class)

    /**
     * Convenience method for obtaining the logger created for this class
     * when calling from Java code, avoiding the `get` prefix.
     */
    public fun logger(): Logger = logger

    /**
     * The shortcut for `logger.atError()`.
     */
    public val atError: Logger.Api get() = logger.atError()

    /**
     * The shortcut for `logger.atSevere()`.
     */
    public val atSevere: Logger.Api get() = logger.atSevere()

    /**
     * The shortcut for `logger.atWarning()`.
     */
    public val atWarning: Logger.Api get() = logger.atWarning()

    /**
     * The shortcut for `logger.atInfo()`.
     */
    public val atInfo: Logger.Api get() = logger.atInfo()

    /**
     * The shortcut for `logger.atConfig()`.
     */
    public val atConfig: Logger.Api get() = logger.atConfig()

    /**
     * The shortcut for `logger.atDebug()`.
     */
    public val atDebug: Logger.Api get() = logger.atDebug()

    /**
     * The shortcut for `logger.atFine()`.
     */
    public val atFine: Logger.Api get() = logger.atFine()

    /**
     * The shortcut for `logger.atFiner()`.
     */
    public val atFiner: Logger.Api get() = logger.atFiner()

    /**
     * The shortcut for `logger.atTrace()`.
     */
    public val atTrace: Logger.Api get() = logger.atTrace()

    /**
     * The shortcut for `logger.atFinest()`.
     */
    public val atFinest: Logger.Api get() = logger.atFinest()
}
