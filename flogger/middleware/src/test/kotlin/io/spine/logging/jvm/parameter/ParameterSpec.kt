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

package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatOptions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [Parameter].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/parameter/ParameterTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`Parameter` should")
internal class ParameterSpec {

    @Test
    fun `not accept nullable options`() {
        assertThrows<IllegalArgumentException> {
            TestParameter(null, 0)
        }
    }

    @Test
    fun `not accept a negative index`() {
        assertThrows<IllegalArgumentException> {
            val options = FormatOptions.getDefault()
            TestParameter(options, -1)
        }
    }

    @Test
    fun `use the given options`() {
        val options = FormatOptions.parse("-2.2", 0, 4, false)
        val parameter = TestParameter(options, 0)
        parameter.formatOptions shouldBeSameInstanceAs options
    }

    @Test
    fun `use the given index`() {
        val options = FormatOptions.getDefault()
        val parameter = TestParameter(options, 0)
        parameter.index shouldBe 0
    }
}

private class TestParameter(options: FormatOptions?, index: Int) : Parameter(options, index) {

    override fun accept(visitor: ParameterVisitor?, value: Any?) {
        throw UnsupportedOperationException()
    }

    override fun getFormat(): String {
        throw UnsupportedOperationException()
    }
}
