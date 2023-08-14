/*
 * Copyright (C) 2018 The Flogger Authors.
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

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`KeyValueFormatter` should")
internal class KeyValueFormatterSpec {

    @Test
    fun `keep the message unchanged if no key-value pairs handled`() {
        var out = StringBuilder()
        formatter(out).done()
        "$out".shouldBeEmpty()

        out = StringBuilder("Hello World")
        formatter(out).done()
        "$out" shouldBe "Hello World"
    }

    @Test
    fun `separate key-value pairs from the message`() {
        // Case NO. 1: empty message.
        var out = StringBuilder()
        var kvf = formatter(out)
        kvf.handle("foo", 23)
        kvf.done()
        "$out" shouldBe "<< foo=23 >>"

        // Case NO. 2: one-line message.
        out = StringBuilder("Message")
        kvf = formatter(out)
        kvf.handle("foo", 23)
        kvf.done()
        "$out" shouldBe "Message << foo=23 >>"

        // Case NO.3: multi-line message.
        out = StringBuilder("Multi\nLine")
        kvf = formatter(out)
        kvf.handle("foo", 23)
        kvf.done()
        "$out" shouldBe "Multi\nLine\n<< foo=23 >>"
    }

    @Test
    fun `handle multiple key-value pairs`() {
        val out = StringBuilder("Message")
        val kvf = formatter(out)
        kvf.handle("foo", 23)
        kvf.handle("bar", false)
        kvf.done()
        "$out" shouldBe "Message << foo=23 bar=false >>"
    }


    @Test
    fun `quote string-like values`() {
        // Safe types are not quoted.
        format("x", 23) shouldBe "x=23"
        format("x", 23L) shouldBe "x=23"
        format("x", 1.23F) shouldBe "x=1.23"
        format("x", 1.23) shouldBe "x=1.23"
        format("x", 1.00) shouldBe "x=1.0"
        format("x", true) shouldBe "x=true"

        // It is not 100% clear what's the best thing to do with a null value. It can exist
        // because of tags and custom keys. For now, treat it as a positive boolean.
        format("x", null) shouldBe "x=true"

        // Enums are currently quoted, but wouldn't need to be if the `name()`
        // rather than `toString()` was used to generate to value.
        format("x", Foo.BAR) shouldBe "x=\"BAR\""

        // Strings, characters and unknown types are quoted.
        format("x", "tag") shouldBe "x=\"tag\""
        format("x", 'y') shouldBe "x=\"y\""
        format("x", StringBuilder("foo")) shouldBe "x=\"foo\""

        // In general, `toString()` method is used.
        val foo = object {
            override fun toString(): String {
                return "unsafe"
            }
        }
        format("x", foo) shouldBe "x=\"unsafe\""
    }

    @Test
    fun `escape special characters`() {
        format("x", "Double \"Quotes\"") shouldBe "x=\"Double \\\"Quotes\\\"\""
        format("x", "\\Backslash\\") shouldBe "x=\"\\\\Backslash\\\\\""
        format("x", "New\nLine") shouldBe "x=\"New\\nLine\""
        format("x", "Carriage\rReturn") shouldBe "x=\"Carriage\\rReturn\""
        format("x", "\tTab") shouldBe "x=\"\\tTab\""

        /*
        Windows and Linux differently handle this replacement character.
        So, the test relies on its HEX code instead of hard-coded literal.
         */
        val replacementChar = '\uFFFD'
        format("x", "Unsafe\u0000Chars") shouldBe "x=\"Unsafe" + replacementChar + "Chars\""

        // Surrogate pairs are preserved rather than being escaped.
        format("x", "\uD83D\uDE00") shouldBe "x=\"\uD83D\uDE00\""
    }
}

private enum class Foo { BAR }

private fun formatter(out: StringBuilder): KeyValueFormatter = KeyValueFormatter("<< ", " >>", out)

@Suppress("SameParameterValue") // Better reads when the parameter is passed explicitly.
private fun format(key: String, value: Any?): String {
    val out = StringBuilder()
    val kvf = KeyValueFormatter("", "", out)
    kvf.handle(key, value)
    kvf.done()
    return "$out"
}
