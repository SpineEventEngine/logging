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

package io.spine.logging.context

import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [ScopedLoggingContext].
 *
 * Please note, concrete implementations of [ScopedLoggingContext] are tested through
 * inheriting from `AbstractScopedLoggingContextTest`. Take a look on `flogger-testing` module.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/context/ScopedLoggingContextTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`ScopedLoggingContext` should")
internal class ScopedLoggingContextSpec {

    @Test
    fun `handle non-user errors`() {
        val exception = assertThrows<InvalidLoggingContextStateException> {
            ErrorContext.newContext().run { }
        }
        exception.cause.shouldBeInstanceOf<IllegalArgumentException>()
        exception.cause!!.shouldHaveMessage("BAD CONTEXT")
    }

    @Test
    fun `propagate user errors`() {
        val exception = assertThrows<IllegalArgumentException> {
            ErrorContext
                .newContext()
                .run { throw IllegalArgumentException("User error") }
        }
        exception.shouldBeInstanceOf<IllegalArgumentException>()
        exception.shouldHaveMessage("User error")
    }
}

/**
 * A context that fails when the scope is closed.
 *
 * It is used to verify that user errors are prioritized in cases
 * where errors cause scopes to be exited.
 */
private object ErrorContext : ScopedLoggingContext() {

    override fun newContext(): Builder = object : Builder() {
        override fun install(): AutoCloseable {
            return AutoCloseable { throw IllegalArgumentException("BAD CONTEXT") }
        }
    }

    override fun newContext(scopeType: ScopeType?): Builder = newContext()

    override fun addTags(tags: Tags): Boolean = false

    override fun applyLogLevelMap(logLevelMap: LogLevelMap): Boolean = false
}
