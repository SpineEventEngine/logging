/*
 * Copyright (C) 2013 The Flogger Authors.
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

package com.google.common.flogger.backend

import com.google.common.flogger.backend.FormatChar.BOOLEAN
import com.google.common.flogger.backend.FormatChar.CHAR
import com.google.common.flogger.backend.FormatChar.DECIMAL
import com.google.common.flogger.backend.FormatChar.EXPONENT
import com.google.common.flogger.backend.FormatChar.EXPONENT_HEX
import com.google.common.flogger.backend.FormatChar.FLOAT
import com.google.common.flogger.backend.FormatChar.GENERAL
import com.google.common.flogger.backend.FormatChar.HEX
import com.google.common.flogger.backend.FormatChar.OCTAL
import com.google.common.flogger.backend.FormatChar.STRING
import com.google.common.flogger.backend.FormatOptions.parse
import com.google.common.flogger.backend.FormatOptions.parseValidFlags
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
