/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.parser.ParseException
import com.google.common.flogger.testing.FakeLogData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

import java.math.BigDecimal
import java.math.BigInteger
import java.util.FormatFlagsConversionMismatchException
import java.util.Formattable
import java.util.Locale
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`BaseMessageFormatter` should")
internal class BaseMessageFormatterSpec {

    @Test
    fun `format strings`() {
        formatPrintf("Hello World") shouldBe "Hello World"
        formatPrintf("Hello %s", "World") shouldBe "Hello World"
        formatPrintf("Hello %s", null) shouldBe "Hello null"
        formatPrintf("Hello %%s") shouldBe "Hello %s"
        // With no arguments, log statements treat the value as a literal and don't escape.
        formatLiteral("Hello %s") shouldBe "Hello %s"
    }

    @Test
    fun `report usage errors`() {
        formatPrintf("Hello %s %s", "World") shouldBe "Hello World [ERROR: MISSING LOG ARGUMENT]"
        formatPrintf(
            "Hello %d",
            "World"
        ) shouldBe "Hello [INVALID: format=%d, type=java.lang.String, value=World]"
    }

    @Test
    fun `use the given 'Formattable'`() {
        val arg = Formattable { formatter, flags, width, precision ->
            formatter.format(null as Locale?, "[f=%d,w=%d,p=%d]", flags, width, precision)
        }
        formatPrintf("%s", arg) shouldBe "[f=0,w=-1,p=-1]"
        formatPrintf("%100s", arg) shouldBe "[f=0,w=100,p=-1]"
        formatPrintf("%.25s", arg) shouldBe "[f=0,w=-1,p=25]"
        formatPrintf("%100.25s", arg) shouldBe "[f=0,w=100,p=25]"
        formatPrintf("%-100s", arg) shouldBe "[f=1,w=100,p=-1]"
        formatPrintf("%S", arg) shouldBe "[f=2,w=-1,p=-1]"
        formatPrintf("%#s", arg) shouldBe "[f=4,w=-1,p=-1]"
        formatPrintf("%-#32.16S", arg) shouldBe "[f=7,w=32,p=16]"
    }

    @Test
    fun `format numbers`() {
        // Should add more tests with other flags: ',', ' ', '-', '+'.
        formatPrintf("%d", -123) shouldBe "-123"
        formatPrintf("%d", -123L) shouldBe "-123"
        formatPrintf("%G", -123f) shouldBe "-123.000"
        formatPrintf("%e", -123f) shouldBe "-1.230000e+02"
        formatPrintf("%f", -123f) shouldBe "-123.000000"
        formatPrintf("%g", -123.456789) shouldBe "-123.457"
        formatPrintf("%.6G", -123.456789) shouldBe "-123.457" // Precision is ignored
        formatPrintf("%.8E", -123.456789) shouldBe "-1.23456789E+02"
        formatPrintf("%f", -123.456789) shouldBe "-123.456789"

        formatPrintf("%(d", 123) shouldBe "123"
        formatPrintf("%(d", -123) shouldBe "(123)"
        formatPrintf("%(d", -123L) shouldBe "(123)"
        formatPrintf("%(g", -123f) shouldBe "(123.000)"
        formatPrintf("%(E", -123f) shouldBe "(1.230000E+02)"
        formatPrintf("%(f", -123f) shouldBe "(123.000000)"
        formatPrintf("%(.0f", -123f) shouldBe "(123)"
        formatPrintf("%(4.10f", -123f) shouldBe "(123.0000000000)"
        formatPrintf("%(1.2f", -123f) shouldBe "(123.00)"
        formatPrintf("%(.2f", -123f) shouldBe "(123.00)"
        formatPrintf("%(f", -123.0) shouldBe "(123.000000)"

        // Hex int and BigInteger
        formatPrintf("%x", 123) shouldBe "7b"
        formatPrintf("%X", -123) shouldBe "FFFFFF85"
        formatPrintf("%x", BigInteger.valueOf(123)) shouldBe "7b"
        formatPrintf("%X", BigInteger.valueOf(-123)) shouldBe "-7B"
        formatPrintf("%(x", BigInteger.valueOf(-123)) shouldBe "(7b)"
        formatPrintf("%(x", BigInteger.valueOf(123)) shouldBe "7b"

        // Octal ints and BigInteger
        formatPrintf("%o", 123) shouldBe "173"
        formatPrintf("%o", -123) shouldBe "37777777605"
        formatPrintf("%o", BigInteger.valueOf(123)) shouldBe "173"
        formatPrintf("%o", BigInteger.valueOf(-123)) shouldBe "-173"
        formatPrintf("%(o", BigInteger.valueOf(-123)) shouldBe "(173)"
        formatPrintf("%(o", BigInteger.valueOf(123)) shouldBe "173"

        // BigDecimal
        formatPrintf("%f", BigDecimal.ONE) shouldBe "1.000000"
        formatPrintf("%f", BigDecimal.valueOf(-1234.56789)) shouldBe "-1234.567890"
        formatPrintf("%g", BigDecimal.ONE) shouldBe "1.00000"
        formatPrintf("%g", BigDecimal.valueOf(-123456789)) shouldBe "-1.23457e+08"
        formatPrintf("%G", BigDecimal.valueOf(-1234.56789)) shouldBe "-1234.57"
        formatPrintf("%G", BigDecimal.valueOf(-123456789)) shouldBe "-1.23457E+08"
        formatPrintf("%e", BigDecimal.valueOf(1234.56789)) shouldBe "1.234568e+03"
        formatPrintf("%E", BigDecimal.valueOf(-1234.56789)) shouldBe "-1.234568E+03"
        formatPrintf("%(f", BigDecimal.valueOf(-1234.56789)) shouldBe "(1234.567890)"
        formatPrintf("%(g", BigDecimal.valueOf(-1234.56789)) shouldBe "(1234.57)"
        formatPrintf("%(e", BigDecimal.valueOf(-1234.56789)) shouldBe "(1.234568e+03)"

        // '#' tests
        formatPrintf("%#o", -123) shouldBe "037777777605"
        formatPrintf("%#x", 123) shouldBe "0x7b"
        formatPrintf("%#X", 123) shouldBe "0X7B"
    }

    @Test
    fun `fail on invalid flags`() {
        shouldThrow<ParseException> { formatPrintf("%(s", 123) }
        shouldThrow<ParseException> { formatPrintf("%(b", 123) }
        shouldThrow<ParseException> { formatPrintf("%(s", -123) }
        shouldThrow<ParseException> { formatPrintf("%(b", -123) }

        shouldThrow<ParseException> { formatPrintf("%#h", "foo") }
        shouldThrow<ParseException> { formatPrintf("%#b", true) }
        shouldThrow<ParseException> { formatPrintf("%#d", 123) }
        shouldThrow<ParseException> { formatPrintf("%#g", BigDecimal.ONE) }
    }

    @Test
    fun `fail on flag-value mismatch`() {
        shouldThrow<FormatFlagsConversionMismatchException> { formatPrintf("%(x", 123) }
        shouldThrow<FormatFlagsConversionMismatchException> { formatPrintf("%(o", 123) }
        shouldThrow<FormatFlagsConversionMismatchException> { formatPrintf("%(x", -123) }
        shouldThrow<FormatFlagsConversionMismatchException> { formatPrintf("%(o", -123) }
    }
}

@Suppress("SameParameterValue") // Extracted for readability.
private fun formatLiteral(value: Any): String {
    val logData = FakeLogData.of(value)
    val out = StringBuilder()
    BaseMessageFormatter.appendFormattedMessage(logData, out)
    return "$out"
}

private fun formatPrintf(msg: String, vararg args: Any?): String {
    val logData: LogData = FakeLogData.withPrintfStyle(msg, *args)
    val out = StringBuilder()
    BaseMessageFormatter.appendFormattedMessage(logData, out)
    return "$out"
}
