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
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// Implementation is tested via subclasses of AbstractScopedLoggingContextTest.
internal class ScopedLoggingContextSpec {

    companion object {

        // A context, which fails when the scope is closed. Used to verify that user errors are
        // prioritized in cases where errors cause scopes to be exited.
        private val ERROR_CONTEXT: ScopedLoggingContext = object : ScopedLoggingContext() {

            override fun newContext(): Builder = object : Builder() {
                override fun install(): LoggingContextCloseable {
                    return LoggingContextCloseable { throw IllegalArgumentException("BAD CONTEXT") }
                }
            }

            override fun newContext(scopeType: ScopeType?): Builder = newContext()

            override fun addTags(tags: Tags?): Boolean = false

            override fun applyLogLevelMap(logLevelMap: LogLevelMap?): Boolean = false
        }
    }

    @Test
    fun testErrorHandlingWithoutUserError() {
        val e = assertThrows<InvalidLoggingContextStateException> {
            ERROR_CONTEXT.newContext().run { }
        }
        assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("BAD CONTEXT")
    }

    @Test
    fun testErrorHandlingWithUserError() {
        val e = assertThrows<IllegalArgumentException> {
            ERROR_CONTEXT
                .newContext()
                .run { throw IllegalArgumentException("User error") }
        }
        assertThat(e).hasMessageThat().isEqualTo("User error")
    }
}
