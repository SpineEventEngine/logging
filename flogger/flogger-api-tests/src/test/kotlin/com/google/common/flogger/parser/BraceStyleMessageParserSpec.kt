/*
 * Copyright (C) 2012 The Flogger Authors.
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

package com.google.common.flogger.parser

import com.google.common.flogger.parser.BraceStyleMessageParser.nextBraceFormatTerm
import com.google.common.flogger.parser.given.FakeParameter
import com.google.common.flogger.parser.given.assertParse
import com.google.common.flogger.parser.given.assertParseError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BraceStyleMessageParserSpec {

    @Test
    fun testBraceFormatNextTerm() {
        nextBraceFormatTerm("", 0) shouldBe -1
        nextBraceFormatTerm("{", 0) shouldBe 0
        nextBraceFormatTerm("Hello {0} World {1}", 0) shouldBe 6
        nextBraceFormatTerm("Hello {0} World {1}", 6) shouldBe 6
        nextBraceFormatTerm("Hello {0} World {1}", 7) shouldBe 16
        nextBraceFormatTerm("Hello ''{0}'' World", 0) shouldBe 8
        nextBraceFormatTerm("Hello {0} World {1}", 17) shouldBe -1
        nextBraceFormatTerm("Hello '{'0} World", 0) shouldBe -1
        nextBraceFormatTerm("Hello '{0}' World", 0) shouldBe -1
    }

    @Test
    fun `fail when facing a trailing single quote`() {
        assertThrows<ParseException> {
            nextBraceFormatTerm("Hello '", 0)
        }.also { exception ->
            exception.message shouldContain "[']"
        }
    }

    @Test
    fun `fail when facing an unmatched single quote`() {
        assertThrows<ParseException> {
            nextBraceFormatTerm("Hello 'World", 0)
        }.also { exception ->
            exception.message shouldContain "['World]"
        }
    }

    @Test
    fun testParseBraceFormatNoTerms() {
        assertParse(fakeParser, "Hello World")
        assertParse(fakeParser, "Hello {0} {1} {2} World", "0", "1", "2")
        assertParse(fakeParser, "Hello {1,XX} {0,YYY} World", "1:XX", "0:YYY")
        assertParse(fakeParser, "Hello '{1}'=''{0}'' World", "0")
    }

    @Test
    fun testParseBraceFormatError() {
        assertParseError(fakeParser, "'", "[']")
        assertParseError(fakeParser, "Hello '", "[']")
        assertParseError(fakeParser, "Hello ' World", "[' World]")
        // Unterminated parameter
        assertParseError(fakeParser, "Hello {", "[{]")
        assertParseError(fakeParser, "Hello {123", "[{123]")
        assertParseError(fakeParser, "Hello {123,xyz", "[{123,xyz]")
        // Missing index
        assertParseError(fakeParser, "Hello {} World", "[{}]")
        assertParseError(fakeParser, "Hello {,} World", "[{,]")
        // Leading zeros
        assertParseError(fakeParser, "Hello {00} World", "[00]")
        assertParseError(fakeParser, "Hello {01} World", "[01]")
        // Index too large
        assertParseError(fakeParser, "Hello {1000000} World", "[1000000]")
        // Malformed index
        assertParseError(fakeParser, "Hello {123x} World", "[123x]")
    }

    @Test
    fun testUnescapeBraceFormat() {
        unescapeBraceFormat("") shouldBe ""
        unescapeBraceFormat("Hello World") shouldBe "Hello World"
        unescapeBraceFormat("Hello '{}' World") shouldBe "Hello {} World"
        unescapeBraceFormat("Hello \'{}\' World") shouldBe "Hello {} World"
        unescapeBraceFormat("Hello \'{}' World") shouldBe "Hello {} World"
        unescapeBraceFormat("Hello '' World") shouldBe "Hello ' World"
        unescapeBraceFormat("Hello \'\' World") shouldBe "Hello ' World"
        unescapeBraceFormat("Hello '\' World") shouldBe "Hello ' World"
        unescapeBraceFormat("He'llo'' ''Wor'ld") shouldBe "Hello World"
        unescapeBraceFormat("He'llo'\' \''Wor\'ld") shouldBe "Hello World"
    }

    @Test
    fun testUnescapeBraceFormatIgnoresErrors() {
        unescapeBraceFormat("Hello '") shouldBe "Hello "
        unescapeBraceFormat("Hello \\'") shouldBe "Hello "
    }
}

/**
 * Simply generates detail strings of the terms it was asked to parse.
 */
private val fakeParser = object : BraceStyleMessageParser() {
    public override fun parseBraceFormatTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        formatStart: Int,
        termEnd: Int
    ) {
        message[termStart] shouldBe '{'
        message[termEnd - 1] shouldBe '}'

        val indexEnd = if (formatStart != -1) formatStart else termEnd
        message.substring(termStart + 1, indexEnd - 1).toInt() shouldBe index

        var detail = ""
        if (formatStart != -1) {
            message[formatStart - 1] shouldBe ','
            detail = message.substring(formatStart, termEnd - 1)
        }

        builder.addParameter(termStart, termEnd, FakeParameter(index, detail))
    }
}

/**
 * Unescapes the characters in the given [message] according
 * to brace formatting rules.
 */
private fun unescapeBraceFormat(message: String): String {
    val out = StringBuilder()
    BraceStyleMessageParser.unescapeBraceFormat(out, message, 0, message.length)
    return out.toString()
}
