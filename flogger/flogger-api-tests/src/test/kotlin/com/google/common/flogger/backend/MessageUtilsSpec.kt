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

import com.google.common.flogger.LogSite
import com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_ALT_FORM
import com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_LEADING_ZEROS
import com.google.common.flogger.backend.FormatOptions.FLAG_UPPER_CASE
import com.google.common.flogger.backend.FormatOptions.UNSET
import com.google.common.flogger.backend.MessageUtils.appendHex
import com.google.common.flogger.backend.MessageUtils.appendLogSite
import com.google.common.flogger.backend.MessageUtils.safeFormatTo
import com.google.common.flogger.backend.MessageUtils.safeToString
import com.google.common.flogger.backend.given.BadObject
import com.google.common.flogger.testing.FakeLogSite.create
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`MessageUtils` should")
internal class MessageUtilsSpec {

    companion object {
        private val NO_OPTIONS = FormatOptions.getDefault()
        private val UPPER_CASE = FormatOptions.of(FLAG_UPPER_CASE, UNSET, UNSET)
    }

    @Nested
    inner class
    `safely convert to string` {

        @Test
        fun `literals and arrays`() {
            safeToString("Hello World") shouldBeSameInstanceAs "Hello World"
            safeToString(10) shouldBe "10"
            safeToString(false) shouldBe "false"

            // Not what you would normally get from `Any.toString()` ...
            safeToString(arrayOf("Foo", "Bar")) shouldBe "[Foo, Bar]"
            safeToString(arrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
            safeToString(null) shouldBe "null"
        }

        @Test
        fun `objects that return 'null' on 'toString()'`() {
            val badObject = BadObject() // Its `toString()` method returns `null`.
            safeToString(badObject) shouldContain badObject::class.simpleName!!
            safeToString(badObject) shouldContain "toString() returned null"
        }

        @Test
        fun `objects that throw on 'toString()'`() {
            val any = object {
                override fun toString(): String = throw IllegalArgumentException("Badness")
            }
            safeToString(any) shouldContain "java.lang.IllegalArgumentException: Badness"
        }
    }

    @Nested
    inner class
    `safely format the given 'Formattable', ` {

        @Test
        fun `which doesn't throw`() {
            val out = StringBuilder()
            val arg = Formattable { formatter, flags, width, precision ->
                formatter.format(null as Locale?, "[f=%d,w=%d,p=%d]", flags, width, precision)
            }

            // FormattableFlags.LEFT_JUSTIFY == 1 << 0 = 1
            // FormattableFlags.UPPERCASE == 1 << 1 = 2
            // FormattableFlags.ALTERNATE == 1 << 2 = 4
            safeFormatTo(arg, out, FormatOptions.of(FLAG_UPPER_CASE, 4, 2))
            "$out" shouldBe "[f=2,w=4,p=2]"

            // Not all flags are passed into the callback.
            val options = FormatOptions.of(FLAG_SHOW_LEADING_ZEROS + FLAG_SHOW_ALT_FORM, 1, 0)
            out.setLength(0)
            safeFormatTo(arg, out, options)
            "$out" shouldBe "[f=4,w=1,p=0]"
        }

        @Test
        fun `which throws an exception`() {
            val badFormattable = Formattable { formatter, _, _, _ ->
                formatter.format(null as Locale?, "DISCARDED")
                throw IllegalArgumentException("Badness")
            }
            val out = StringBuilder()
            safeFormatTo(badFormattable, out, FormatOptions.getDefault())
            "$out" shouldContain "java.lang.IllegalArgumentException: Badness"
            "$out" shouldNotContain "DISCARDED"
        }
    }

    @Test
    fun `append log site`() {
        val out = StringBuilder()
        val logSite = create("<class>", "<method>", 32, "Ignored.java")

        appendLogSite(logSite, out).shouldBeTrue()
        "$out" shouldBe "<class>.<method>:32"

        out.setLength(0)
        appendLogSite(LogSite.INVALID, out).shouldBeFalse()
        "$out".shouldBeEmpty()
    }

    @Test
    fun `append HEX values`() {
        formatHex(0xFDB97531, NO_OPTIONS) shouldBe "fdb97531"
        formatHex(0xFDB97531, UPPER_CASE) shouldBe "FDB97531"
        formatHex(0x0123456789ABCDEFL, NO_OPTIONS) shouldBe "123456789abcdef"
        formatHex(0x1CDCBA9776543210L, UPPER_CASE) shouldBe "1CDCBA9776543210"
        formatHex(0, UPPER_CASE) shouldBe "0"
        formatHex((-1).toByte(), UPPER_CASE) shouldBe "FF"
        formatHex((-1).toShort(), UPPER_CASE) shouldBe "FFFF"
    }
}

private fun formatHex(n: Number, options: FormatOptions): String {
    val out = StringBuilder()
    appendHex(out, n, options)
    return "$out"
}
