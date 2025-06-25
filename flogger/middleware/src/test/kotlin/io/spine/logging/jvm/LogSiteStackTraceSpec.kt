/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [LogSiteStackTrace].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/LogSiteStackTraceTest.java">
 *     Original Java code of Google Flogger</a>
 */
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
