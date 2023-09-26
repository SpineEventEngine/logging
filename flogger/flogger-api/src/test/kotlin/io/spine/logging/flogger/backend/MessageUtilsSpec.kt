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

package io.spine.logging.flogger.backend

import io.spine.logging.flogger.backend.FormatOptions.FLAG_SHOW_ALT_FORM
import io.spine.logging.flogger.backend.FormatOptions.FLAG_SHOW_LEADING_ZEROS
import io.spine.logging.flogger.backend.FormatOptions.FLAG_UPPER_CASE
import io.spine.logging.flogger.backend.FormatOptions.UNSET
import io.spine.logging.flogger.backend.MessageUtils.appendHex
import io.spine.logging.flogger.backend.MessageUtils.appendLogSite
import io.spine.logging.flogger.backend.MessageUtils.safeFormatTo
import io.spine.logging.flogger.backend.MessageUtils.safeToString
import io.spine.logging.backend.given.BadToString
import com.google.common.flogger.testing.FakeLogSite.create
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.flogger.LogSite
import java.util.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [MessageUtils].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/MessageUtilsTest.java">
 *     Original Java code of Google Flogger</a>
 */
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
            val badToString = BadToString()
            safeToString(badToString) shouldContain badToString::class.simpleName!!
            safeToString(badToString) shouldContain "toString() returned null"
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
    `safely format the given 'Formattable'` {

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
