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

import io.spine.logging.backend.Platform
import kotlin.reflect.KClass
import io.spine.logging.jvm.JvmLogSite

/**
 * Determines log sites for the current line of code using Flogger utils.
 */
public actual object LogSiteLookup {

    /**
     * Returns a [LogSite] for the caller of the specified class.
     *
     * If log site determination is unsupported, this method returns
     * the [LogSite.Invalid] instance.
     */
    public actual fun callerOf(loggingApi: KClass<*>): LogSite {
        val jvmSite = JvmLogSite.callerOf(loggingApi.java)
        val logSite = jvmSite.toLogSite()
        return logSite
    }

    /**
     * Returns a [LogSite] for the current line of code.
     *
     * If log site determination is unsupported, this method returns
     * the [LogSite.Invalid] instance.
     */
    public actual fun logSite(): LogSite {
        val platformLogSite = Platform.getCallerFinder().findLogSite(
            LogSiteLookup::class.java,
            0
        )
        val logSite = platformLogSite.toLogSite()
        return logSite
    }
}

private fun JvmLogSite.toLogSite(): LogSite {
    if (this == JvmLogSite.invalid) {
        return LogSite.Invalid
    }
    return InjectedLogSite(
        className = this@toLogSite.className,
        methodName = this@toLogSite.methodName,
        lineNumber = this@toLogSite.lineNumber
    )
}
