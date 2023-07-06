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

import com.google.common.flogger.backend.Platform
import kotlin.reflect.KClass
import com.google.common.flogger.LogSite as FloggerLogSite
import com.google.common.flogger.LogSites as FloggerLogSites

/**
 * Determines log sites for the current line of code using Flogger utils.
 */
public actual object LogSites {

    /**
     * Returns a [LogSite] for the caller of the specified class.
     *
     * If log site determination is unsupported, this method returns
     * the [LogSite.INVALID] instance.
     */
    public actual fun callerOf(loggingApi: KClass<*>): LogSite {
        val floggerSite = FloggerLogSites.callerOf(loggingApi.java)
        val logSite = floggerSite.toLogSite()
        return logSite
    }

    /**
     * Returns a [LogSite] for the current line of code.
     *
     * If log site determination is unsupported, this method returns
     * the [LogSite.INVALID] instance.
     */
    public actual fun logSite(): LogSite {
        val floggerSite = Platform.getCallerFinder().findLogSite(
            LogSites::class.java,
            0
        )
        val logSite = floggerSite.toLogSite()
        return logSite
    }
}

private fun FloggerLogSite.toLogSite(): LogSite {
    if (this == FloggerLogSite.INVALID) {
        return LogSite.INVALID
    }
    return InjectedLogSite(
        className = this@toLogSite.className,
        methodName = this@toLogSite.methodName,
        lineNumber = this@toLogSite.lineNumber
    )
}
