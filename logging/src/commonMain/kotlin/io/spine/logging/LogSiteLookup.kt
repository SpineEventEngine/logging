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

import kotlin.reflect.KClass

/**
 * Determines log sites for the current line of code.
 *
 * Note that determining of a log site at runtime can be a slow
 * operation because it usually involves some form of stack trace analysis.
 *
 * Methods of this class can be used with the [LoggingApi.withInjectedLogSite]
 * method to implement logging helper methods.
 */
public expect object LogSiteLookup {

    /**
     * Returns a [LogSite] for the caller of the specified class.
     *
     * In some platforms, log site determination may be unsupported, and in
     * those cases this method should return the [LogSite.INVALID] instance.
     */
    public fun callerOf(loggingApi: KClass<*>): LogSite

    /**
     * Returns a [LogSite] for the current line of code.
     *
     * In some platforms, log site determination may be unsupported, and in
     * those cases this method should return the [LogSite.INVALID] instance.
     */
    public fun logSite(): LogSite
}
