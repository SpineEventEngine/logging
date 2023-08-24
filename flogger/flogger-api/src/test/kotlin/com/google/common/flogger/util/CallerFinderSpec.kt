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

package com.google.common.flogger.util

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [CallerFinder].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/util/CallerFinderTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`CallerFinder` should")
internal class CallerFinderSpec {

    /**
     * A sanity check if we ever discover a platform where the class name
     * in the stack trace does not match [Class.getName] – this is never quite
     * guaranteed by the JavaDoc in the JDK but is relied upon during log site analysis.
     */
    @Test
    fun `use the class name that matches one in the stack trace`() {
        // Simple case for a top-level named class.
        Throwable().stackTrace[0].className shouldBe CallerFinderSpec::class.java.name

        // Anonymous inner class.
        val obj = object {
            override fun toString(): String {
                return Throwable().stackTrace[0].className
            }
        }

        "$obj" shouldBe obj::class.java.name
    }

    @Test
    fun `find the stack trace element of the immediate caller of the specified class`() {
        // There are 2 internal methods (not including the log method itself)
        // in our fake library.
        val library = LoggerCode(skipCount = 2)
        val code = UserCode(library)
        code.invokeUserCode()
        library.caller shouldNotBe null
        library.caller!!.className shouldBe UserCode::class.java.name
        library.caller!!.methodName shouldBe "loggingMethod"
    }

    @Test
    fun `return 'null' due to wrong skip count`() {
        // If the minimum offset exceeds the number of internal methods, the find fails.
        val library = LoggerCode(skipCount = 3)
        val code = UserCode(library)
        code.invokeUserCode()
        library.caller shouldBe null
    }
}
