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

package io.spine.logging.flogger.parser

import io.spine.logging.flogger.parser.PrintfMessageParser.nextPrintfTerm
import io.spine.logging.flogger.parser.given.FakeParameter
import io.spine.logging.flogger.parser.given.assertParse
import io.spine.logging.flogger.parser.given.assertParseError

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [PrintfMessageParser].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/parser/PrintfMessageParserTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`PrintfMessageParserSpec` should")
@Suppress("DANGEROUS_CHARACTERS") // '%' character is needed in the test name.
internal class PrintfMessageParserSpec {

    @Test
    fun `return the index of the next unquoted '%' character`() {
        nextPrintfTerm("", 0) shouldBe -1
        nextPrintfTerm("%X", 0) shouldBe 0
        nextPrintfTerm("Hello %X World %X", 0) shouldBe 6
        nextPrintfTerm("Hello %X World %X", 6) shouldBe 6
        nextPrintfTerm("Hello %X World %X", 7) shouldBe 15
        nextPrintfTerm("Hello %% World %X", 0) shouldBe 15
        nextPrintfTerm("Hello %X World %X", 16) shouldBe -1
    }

    @Test
    fun `fail when facing a trailing percent sign`() {
        val exception = assertThrows<ParseException> {
            nextPrintfTerm("Hello %", 0)
        }
        exception.message shouldContain "[%]"
    }

    @Test
    fun `extract place-holder terms`() {
        assertParse(fakeParser, "Hello World")
        assertParse(fakeParser, "Hello %A %B %C World", "0:A", "1:B", "2:C")
        assertParse(fakeParser, "Hello %1\$A %2\$B %3\$C World", "0:A", "1:B", "2:C")
        assertParse(fakeParser, "Hello %2\$A %B %C %1\$D World", "1:A", "0:B", "1:C", "0:D")
        assertParse(fakeParser, "Hello %A %<B %<C World", "0:A", "0:B", "0:C")
        assertParse(fakeParser, "Hello %???X World", "0:???:X")
        assertParse(fakeParser, "%%%A%%X%%%B%%", "0:A", "1:B")
    }

    @Test
    fun `fail when facing an unexpected format`() {
        assertParseError(fakeParser, "%", "[%]")
        assertParseError(fakeParser, "Hello %", "[%]")
        // Unterminated parameter
        assertParseError(fakeParser, "Hello %1", "[%1]")
        assertParseError(fakeParser, "Hello %1$", "[%1$]")
        // Missing index
        assertParseError(fakeParser, "Hello %$ World", "[%$]")
        // Leading zeros
        assertParseError(fakeParser, "Hello %01\$X World", "[%01$]")
        // Index too large
        assertParseError(fakeParser, "Hello %1000000X World", "[%1000000]")
        // Printf indices are 1-based
        assertParseError(fakeParser, "Hello %0\$X World", "[%0$]")
        // Relative indexing cannot come first.
        assertParseError(fakeParser, "Hello %<X World", "[%<]")
        // An unexpected flag or missing term character
        assertParseError(fakeParser, "Hello %????", "[%????]")
        assertParseError(fakeParser, "Hello %X %<", "[%<]")
        // Gaps in term indices is a parse error, report the first argument index not referenced.
        assertParseError(fakeParser, "Hello %X %100\$X World", "first missing index=1")
    }

    @Test
    fun `return the system newline separator`() {
        // This should pass even if "line.separator" is set to something else.
        val nl = PrintfMessageParser.getSafeSystemNewline()
        nl shouldBeIn listOf("\n", "\r", "\r\n")
    }

    @Test
    fun `unescape printf-supported new line`() {
        val nl = PrintfMessageParser.getSafeSystemNewline()
        unescapePrintf("%n") shouldBe nl
        unescapePrintf("Hello %n World") shouldBe "Hello $nl World"
        unescapePrintf("Hello World %n") shouldBe "Hello World $nl"
        unescapePrintf("%n%n%%n%n") shouldBe "$nl$nl%n$nl"
    }

    @Test
    fun `unescape '%' symbol`() {
        unescapePrintf("") shouldBe ""
        unescapePrintf("Hello World") shouldBe "Hello World"
        unescapePrintf("Hello %% World") shouldBe "Hello % World"
        unescapePrintf("Hello %%%% World") shouldBe "Hello %% World"
        unescapePrintf("%% 'Hello {%%}{%%} World' %%") shouldBe "% 'Hello {%}{%} World' %"
    }

    @Test
    fun `unescape unexpected '%' symbols`() {
        unescapePrintf("Hello % World") shouldBe "Hello % World"
        unescapePrintf("Hello %") shouldBe "Hello %"
    }
}

/**
 * Simply generates detail strings of the terms it was asked to parse.
 */
private val fakeParser = object : PrintfMessageParser() {
    override fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int {
        val detail = StringBuilder()
        if (formatStart > specStart) {
            detail.append(message, specStart, formatStart).append(':')
        }
        detail.append(message[formatStart])
        // Assume in tests we are not considering multi-character format specifiers such as "%Tc"
        val termEnd = formatStart + 1
        builder.addParameter(termStart, termEnd, FakeParameter(index, detail.toString()))
        return termEnd
    }
}

/**
 * Unescapes the characters in the given [message] according
 * to printf style formatting rules.
 */
private fun unescapePrintf(message: String): String {
    val out = StringBuilder()
    PrintfMessageParser.unescapePrintf(out, message, 0, message.length)
    return out.toString()
}
