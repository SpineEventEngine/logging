/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.given

import io.spine.logging.LogSite

/**
 * Number of invocations to be performed in tests for a single log site,
 * along with the configured rate limitation.
 */
internal class LogSiteInvocations(
    private val logSite: LogSite,
    private val rate: Int,
    private val invocations: Int
) {
    operator fun component1() = logSite
    operator fun component2() = rate
    operator fun component3() = invocations
}

/**
 * Collections of [LogSiteInvocations].
 *
 * The class provides a convenient method to fulfill the collection.
 */
internal class InvocationsPerSite(
    private val list: MutableList<LogSiteInvocations> = mutableListOf()
) : Collection<LogSiteInvocations> by list {

    /**
     * Adds a new [LogSiteInvocations] with the given parameters.
     */
    fun add(logSite: LogSite, rate: Int, invocations: Int): InvocationsPerSite {
        val logSiteInvocation = LogSiteInvocations(logSite, rate, invocations)
        list.add(logSiteInvocation)
        return this
    }
}
