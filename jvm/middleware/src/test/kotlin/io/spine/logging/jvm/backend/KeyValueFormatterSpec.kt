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

package io.spine.logging.jvm.backend

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [KeyValueFormatter].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/KeyValueFormatterTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`KeyValueFormatter` should")
internal class KeyValueFormatterSpec {

    @Test
    fun `keep the message unchanged if no key-value pairs passed`() {
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
