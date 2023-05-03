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

package io.spine.testing.logging.context

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.ShouldSpec
import io.spine.logging.context.LogLevelMap
import io.spine.logging.context.ScopedLoggingContext

@Suppress("LeakingThis")
abstract class AbstractLogLevelMapSpec: ShouldSpec() {

    private val map = lazy {
        val builder = LogLevelMap.builder()
        setupLogLevelMapBuilder(builder)
        builder.build()
    }

    private val context by lazy {
        ScopedLoggingContext.newContext()
            .withLogLevelMap(map.value)
    }

    private var closable: AutoCloseable? = null

    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        closable = context.install()
    }

    override fun afterSpec(f: suspend (Spec) -> Unit) {
        closable?.close()
        super.afterSpec(f)
    }

    init {
        should("allow setting default level") {
            checkSettingDefaultLevel()
        }
    }

    abstract fun setupLogLevelMapBuilder(builder: LogLevelMap.Builder)
    abstract fun checkSettingDefaultLevel()
}

