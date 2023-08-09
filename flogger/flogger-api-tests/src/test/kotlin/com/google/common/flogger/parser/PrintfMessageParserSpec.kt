/*
 * Copyright (C) 2012 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.google.common.flogger.parser.PrintfMessageParser.nextPrintfTerm
import com.google.common.flogger.parser.given.FakeParameter
import com.google.common.flogger.parser.given.assertParse
import com.google.common.flogger.parser.given.assertParseError

import com.google.common.truth.Truth.assertThat
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PrintfMessageParserSpec {

    @Test
    fun testPrintfNextTerm() {
        nextPrintfTerm("", 0) shouldBe -1
        nextPrintfTerm("%X", 0) shouldBe 0
        nextPrintfTerm("Hello %X World %X", 0) shouldBe 6
        nextPrintfTerm("Hello %X World %X", 6) shouldBe 6
        nextPrintfTerm("Hello %X World %X", 7) shouldBe 15
        nextPrintfTerm("Hello %% World %X", 0) shouldBe 15
        nextPrintfTerm("Hello %X World %X", 16) shouldBe -1
    }

    @Test
    fun testPrintfNextTermFails() {
        val exception = assertThrows<ParseException> {
            nextPrintfTerm("Hello %", 0)
        }
        exception.message shouldContain "[%]"
    }

    @Test
    fun testParse() {
        assertParse(FakeParser, "Hello World")
        assertParse(FakeParser, "Hello %A %B %C World", "0:A", "1:B", "2:C")
        assertParse(FakeParser, "Hello %1\$A %2\$B %3\$C World", "0:A", "1:B", "2:C")
        assertParse(FakeParser, "Hello %2\$A %B %C %1\$D World", "1:A", "0:B", "1:C", "0:D")
        assertParse(FakeParser, "Hello %A %<B %<C World", "0:A", "0:B", "0:C")
        assertParse(FakeParser, "Hello %???X World", "0:???:X")
        assertParse(FakeParser, "%%%A%%X%%%B%%", "0:A", "1:B")
    }

    @Test
    fun testParsePrintfError() {
        assertParseError(FakeParser, "%", "[%]")
        assertParseError(FakeParser, "Hello %", "[%]")
        // Unterminated parameter
        assertParseError(FakeParser, "Hello %1", "[%1]")
        assertParseError(FakeParser, "Hello %1$", "[%1$]")
        // Missing index
        assertParseError(FakeParser, "Hello %$ World", "[%$]")
        // Leading zeros
        assertParseError(FakeParser, "Hello %01\$X World", "[%01$]")
        // Index too large
        assertParseError(FakeParser, "Hello %1000000X World", "[%1000000]")
        // Printf indices are 1-based
        assertParseError(FakeParser, "Hello %0\$X World", "[%0$]")
        // Relative indexing cannot come first.
        assertParseError(FakeParser, "Hello %<X World", "[%<]")
        // Unexpected flag or missing term character
        assertParseError(FakeParser, "Hello %????", "[%????]")
        assertParseError(FakeParser, "Hello %X %<", "[%<]")
        // Gaps in term indices is a parse error, report the first argument index not referenced.
        assertParseError(FakeParser, "Hello %X %100\$X World", "first missing index=1")
    }

    @Test
    fun testGetSafeSystemNewline() {
        // This should pass even if "line.separator" is set to something else.
        val nl = PrintfMessageParser.getSafeSystemNewline()
        nl shouldBeIn listOf("\n", "\r", "\r\n")
    }

    @Test
    fun testUnescapePrintfSupportsNewline() {
        val nl = PrintfMessageParser.getSafeSystemNewline()
        unescapePrintf("%n") shouldBe nl
        unescapePrintf("Hello %n World") shouldBe "Hello $nl World"
        unescapePrintf("Hello World %n") shouldBe "Hello World $nl"
        unescapePrintf("%n%n%%n%n") shouldBe "$nl$nl%n$nl"
    }

    @Test
    fun testUnescapePrintf() {
        unescapePrintf("") shouldBe ""
        unescapePrintf("Hello World") shouldBe "Hello World"
        unescapePrintf("Hello %% World") shouldBe "Hello % World"
        unescapePrintf("Hello %%%% World") shouldBe "Hello %% World"
        unescapePrintf("%% 'Hello {%%}{%%} World' %%") shouldBe "% 'Hello {%}{%} World' %"
    }

    @Test
    fun testUnescapePrintfIgnoresErrors() {
        unescapePrintf("Hello % World") shouldBe "Hello % World"
        unescapePrintf("Hello %") shouldBe "Hello %"
    }
}

/**
 * Simply generates detail strings of the terms it was asked to parse.
 */
private object FakeParser : PrintfMessageParser() {
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

private fun unescapePrintf(message: String): String {
    val out = StringBuilder()
    PrintfMessageParser.unescapePrintf(out, message, 0, message.length)
    return out.toString()
}
