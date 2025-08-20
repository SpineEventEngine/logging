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

package io.spine.logging.backend.system.given

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.backend.LogCallerFinder
import io.spine.logging.backend.Platform
import io.spine.logging.jvm.AbstractLogger
import io.spine.logging.jvm.JvmLogSite
import kotlin.reflect.KClass

/**
 * No-op implementation of [Platform.LogCallerFinder].
 */
@Immutable
@ThreadSafe
internal class NoOpCallerFinder : LogCallerFinder() {

    /**
     * Throws [IllegalStateException].
     */
    override fun findLoggingClass(loggerClass: KClass<out AbstractLogger<*>>): String =
        throw UnsupportedOperationException()

    /**
     * Throws [IllegalStateException].
     */
    override fun findLogSite(loggerApi: KClass<*>, stackFramesToSkip: Int): JvmLogSite =
        throw UnsupportedOperationException()
}
