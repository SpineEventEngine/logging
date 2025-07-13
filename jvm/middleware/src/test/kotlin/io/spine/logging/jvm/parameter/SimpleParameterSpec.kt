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

package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import io.spine.logging.jvm.backend.FormatChar.DECIMAL
import io.spine.logging.jvm.backend.FormatChar.FLOAT
import io.spine.logging.jvm.backend.FormatChar.HEX
import io.spine.logging.jvm.backend.FormatChar.STRING
import io.spine.logging.jvm.parameter.SimpleParameter.Companion.buildFormatString
import io.spine.logging.jvm.parser.ParseException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.lang.IllegalStateException
import org.junit.jupiter.api.Nested

/**
 * Tests for [SimpleParameter].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/parameter/SimpleParameterTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`SimpleParameter` should")
internal class SimpleParameterSpec {

    private val options = FormatOptions.getDefault()

    @Test
    fun `return the same instance for the same chars without options up to first 10 indices`() {
        for (char: FormatChar in FormatChar.entries) {
            repeat(10) { index ->
                val instance1 = SimpleParameter.of(index, char, options)
                val instance2 = SimpleParameter.of(index, char, options)
                instance1 shouldBeSameInstanceAs instance2
            }
        }
    }

    @Nested
    inner class `not return the same instance` {

        @Test
        fun `for different indices`() {
            val zeroIndex = SimpleParameter.of(0, DECIMAL, options)
            val firstIndex = SimpleParameter.of(1, DECIMAL, options)
            zeroIndex shouldNotBeSameInstanceAs firstIndex
        }

        @Test
        fun `for different format chars`() {
            val decimalChar = SimpleParameter.of(0, DECIMAL, options)
            val floatChar = SimpleParameter.of(0, FLOAT, options)
            decimalChar shouldNotBeSameInstanceAs floatChar
        }

        @Test
        fun `for different formatting options`() {
            val customOptions = FormatOptions.parse("-10", 0, 3, false)
            val withCustomOptions = SimpleParameter.of(0, FLOAT, customOptions)
            val withDefaultOptions = SimpleParameter.of(0, DECIMAL, options)
            withCustomOptions shouldNotBeSameInstanceAs withDefaultOptions
        }
    }

    @Test
    fun `build format string`() {
        buildFormatString(parseOptions("-20", false), STRING) shouldBe "%-20s"
        buildFormatString(parseOptions("0#16", true), HEX) shouldBe "%#016X"
        buildFormatString(parseOptions("+-20", false), DECIMAL) shouldBe "%+-20d"
        buildFormatString(parseOptions(",020.10", false), FLOAT) shouldBe "%,020.10f"
    }
}

private fun parseOptions(s: String, isUpperCase: Boolean): FormatOptions {
    try {
        return FormatOptions.parse(s, 0, s.length, isUpperCase)
    } catch (parseException: ParseException) {
        throw IllegalStateException(parseException)
    }
}
