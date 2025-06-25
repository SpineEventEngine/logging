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

package io.spine.logging.flogger.backend

import io.spine.logging.flogger.backend.FormatChar.BOOLEAN
import io.spine.logging.flogger.backend.FormatChar.CHAR
import io.spine.logging.flogger.backend.FormatChar.DECIMAL
import io.spine.logging.flogger.backend.FormatChar.EXPONENT
import io.spine.logging.flogger.backend.FormatChar.EXPONENT_HEX
import io.spine.logging.flogger.backend.FormatChar.FLOAT
import io.spine.logging.flogger.backend.FormatChar.GENERAL
import io.spine.logging.flogger.backend.FormatChar.HEX
import io.spine.logging.flogger.backend.FormatChar.OCTAL
import io.spine.logging.flogger.backend.FormatChar.STRING
import io.spine.logging.flogger.backend.FormatOptions.parse
import io.spine.logging.flogger.backend.FormatOptions.parseValidFlags
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [FormatChar].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/FormatCharTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`FormatChar` should")
internal class FormatCharSpec {

    @Test
    fun `have the corresponding 'FormatType'`() {
        arrayOf(STRING) shouldFormat FormatType.GENERAL
        arrayOf(CHAR) shouldFormat FormatType.CHARACTER
        arrayOf(DECIMAL, HEX, OCTAL) shouldFormat FormatType.INTEGRAL
        arrayOf(FLOAT, GENERAL, EXPONENT, EXPONENT_HEX) shouldFormat FormatType.FLOAT
    }

    /**
     * Tests the exact set of allowed flags for each format type.
     */
    @Test
    fun `have printf-compatible flags`() {
        // Grouped by similar allowed flags.
        STRING.shouldHaveFlags("-#", true)

        BOOLEAN.shouldHaveFlags("-", true)
        CHAR.shouldHaveFlags("-", true)

        DECIMAL.shouldHaveFlags("(-0+ ,", false)
        GENERAL.shouldHaveFlags("-0(+ ,", true)

        HEX.shouldHaveFlags("-#(0", true)
        OCTAL.shouldHaveFlags("-(#0", false)

        FLOAT.shouldHaveFlags("-#0+ ,(", false)

        EXPONENT.shouldHaveFlags("-#0+ (", true)
        EXPONENT_HEX.shouldHaveFlags("-#0+ ", true)
    }

    /**
     * Tests conditional rules and special cases for flags/width/precisions, etc.
     *
     * These are not exhaustive tests for all illegal formatting options.
     *
     * @see “Details” section in <a href="https://docs.oracle.com/javase/9/docs/api/java/util/Formatter.html">
     *     Formatter docs</a>.
     */
    @Test
    fun `decline invalid options`() {
        // String formatting cannot have zero padding.
        parseOptions("#016").areValidFor(STRING) shouldBe false
        // Integer formatting cannot have precision.
        parseOptions("10.5").areValidFor(DECIMAL) shouldBe false
        // Exponential formatting cannot use grouping (even though other numeric formats do).
        parseOptions(",").areValidFor(EXPONENT) shouldBe false
        // Gereral scientific notation cannot specify a radix.
        parseOptions("#").areValidFor(GENERAL) shouldBe false
        // Octal numbers are never negative, so ' ' is not meaningful.
        parseOptions(" ").areValidFor(OCTAL) shouldBe false
        // Left alignment or zero padding must have a width.
        parseOptions("-").areValidFor(DECIMAL) shouldBe false
        parseOptions("0").areValidFor(HEX) shouldBe false

        // Assert that '(' is not valid for other formats
        parseOptions("(").areValidFor(EXPONENT_HEX) shouldBe false
        parseOptions("(").areValidFor(STRING) shouldBe false
    }
}

private infix fun Array<FormatChar>.shouldFormat(type: FormatType) =
    forEach { fc -> fc.type shouldBeSameInstanceAs type }

private fun FormatChar.shouldHaveFlags(allowedFlagChars: String, hasUpperCase: Boolean) {
    val bitMask = parseValidFlags(allowedFlagChars, hasUpperCase)
    allowedFlags shouldBe bitMask
}

private fun parseOptions(str: String): FormatOptions =
    parse(str, 0, str.length, false /* lower case – ignored */)
