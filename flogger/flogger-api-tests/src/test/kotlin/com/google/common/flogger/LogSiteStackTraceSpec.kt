/*
 * Copyright (C) 2016 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.google.common.flogger

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogSiteStackTrace` should")
internal class LogSiteStackTraceSpec {

    companion object {
        private val FAKE_STACK = arrayOf(
            StackTraceElement("FirstClass", "method1", "Source1.java", 111),
            StackTraceElement("SecondClass", "method2", "Source2.java", 222),
            StackTraceElement("ThirdClass", "method3", "Source3.java", 333)
        )
    }

    @Test
    fun `return message containing the requested stack size`() {
        val trace = LogSiteStackTrace(null, StackSize.FULL, arrayOfNulls(0))
        trace shouldHaveMessage "FULL"
    }

    @Test
    fun `return the given cause`() {
        val cause = RuntimeException()
        val trace = LogSiteStackTrace(cause, StackSize.SMALL, arrayOfNulls(0))
        trace.cause shouldBeSameInstanceAs cause
    }

    @Test
    fun `allow nullable cause`() {
        val trace = LogSiteStackTrace(null, StackSize.NONE, arrayOfNulls(0))
        trace.cause.shouldBeNull()
    }

    @Test
    fun `return the given stack trace`() {
        val trace = LogSiteStackTrace(null, StackSize.SMALL, FAKE_STACK)
        trace.stackTrace shouldNotBeSameInstanceAs FAKE_STACK
        trace.stackTrace shouldBe FAKE_STACK
    }
}
