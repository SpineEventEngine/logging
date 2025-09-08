/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

import io.spine.logging.LogSiteLookup.callerOf
import io.spine.logging.LogSiteLookup.logSite
import io.spine.logging.jvm.MyLogUtil.callerLogSite
import io.spine.logging.jvm.MyLogUtil.callerLogSiteWrapped
import io.kotest.matchers.shouldBe
import io.spine.logging.LogSite
import io.spine.logging.injectedLogSite
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for the utility methods in [LogSite.Companion].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/LogSitesTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`LogSiteLookup` should")
internal class LogSiteLookupSpec {

    @Test
    fun `return log site for the current line of code`() {
        val outerMethod = "return log site for the current line of code"
        logSite().methodName shouldBe outerMethod
    }

    @Test
    fun `return log site for the caller of the specified class`() {
        val outerMethod = "return log site for the caller of the specified class"
        callerLogSite.methodName shouldBe outerMethod
        callerLogSiteWrapped.methodName shouldBe outerMethod
    }

    @Test
    fun `return 'INVALID' log site if the caller not found`() {
        callerOf(String::class) shouldBe LogSite.Invalid
    }

    @Test
    fun `prohibit calling from non-annotated function`() {
        notAllowedCaller()
    }
}

private object MyLogUtil {

    val callerLogSite: LogSite
        get() = callerOf(MyLogUtil::class)

    val callerLogSiteWrapped: LogSite
        get() = callerLogSite
}

private fun notAllowedCaller(): LogSite {
    return injectedLogSite("foo", "bar", 42, "baz.kt")
}
