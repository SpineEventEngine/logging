/*
 * Copyright (C) 2019 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.context

import com.google.common.flogger.context.ScopedLoggingContext.InvalidLoggingContextStateException
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// Concrete implementations are tested via subclasses of `AbstractScopedLoggingContextTest`.
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
        override fun install(): LoggingContextCloseable {
            return LoggingContextCloseable { throw IllegalArgumentException("BAD CONTEXT") }
        }
    }

    override fun newContext(scopeType: ScopeType?): Builder = newContext()

    override fun addTags(tags: Tags?): Boolean = false

    override fun applyLogLevelMap(logLevelMap: LogLevelMap?): Boolean = false
}
