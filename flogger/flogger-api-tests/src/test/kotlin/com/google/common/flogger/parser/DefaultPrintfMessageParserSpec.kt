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

import com.google.common.flogger.backend.FormatChar
import com.google.common.flogger.parser.given.MemoizingMessageBuilder
import com.google.common.flogger.parser.given.MemoizingParameterVisitor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DefaultPrintfMessageParserSpec {

    private val parser = DefaultPrintfMessageParser.getInstance()

    @Test
    fun testParsePrintf() {
        val memoizingBuilder = MemoizingMessageBuilder(parser)
        parser.parsePrintfTerm(memoizingBuilder, 1, "Hello %2$+06.2f World", 6, 9, 14)

        // Check how a parser uses the given builder.
        with(memoizingBuilder) {
            termStart shouldBe 6
            termEnd shouldBe 15
            param.index shouldBe 1
        }

        // Now visit the parameter and remember its state.
        val param = memoizingBuilder.param
        val memoizingVisitor = MemoizingParameterVisitor()
        param.accept(memoizingVisitor, arrayOf("Answer: ", 42.0))

        // Recover the remembered arguments and check that the right formatting was done.
        with(memoizingVisitor) {
            value shouldBe 42
            format shouldBe FormatChar.FLOAT
            options.width shouldBe 6
            options.precision shouldBe 2
            options.shouldShowLeadingZeros() shouldBe true
            options.shouldPrefixPlusForPositiveValues() shouldBe true
        }
    }

    @Test
    fun testUnknownPrintfFormat() {
        val exception = assertThrows<ParseException> {
            parser.parsePrintfTerm(null, 0, "%Q", 0, 1, 1)
        }
        exception.message shouldContain "[%Q]"
    }

    @Test
    fun testInvalidPrintfFlags() {
        val exception = assertThrows<ParseException> {
            parser.parsePrintfTerm(null, 0, "%0s", 0, 1, 2)
        }
        exception.message shouldContain "[%0s]"
    }
}
