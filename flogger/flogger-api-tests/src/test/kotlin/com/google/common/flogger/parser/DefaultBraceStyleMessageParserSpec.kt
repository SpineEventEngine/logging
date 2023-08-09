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

import com.google.common.flogger.backend.FormatChar
import com.google.common.flogger.backend.FormatOptions
import com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_GROUPING
import com.google.common.flogger.backend.FormatOptions.UNSET
import com.google.common.flogger.parser.given.MemoizingMessageBuilder
import com.google.common.flogger.parser.given.MemoizingParameterVisitor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DefaultBraceStyleMessageParserSpec {

    companion object {
        private val PARSER = DefaultBraceStyleMessageParser.getInstance()
        private val WITH_GROUPING = FormatOptions.of(FLAG_SHOW_GROUPING, UNSET, UNSET)
    }

    @Test
    fun testParseBraceFormat() {
        // Parse just 3 characters representing the brace format specifier between position 6 and 9.
        // "-1" indicates that there's no additional formatting information after the index.
        val memoizingBuilder = MemoizingMessageBuilder(PARSER)
        PARSER.parseBraceFormatTerm(memoizingBuilder, 1, "Hello {1} World", 6, -1, 9)

        // Check the parameter created by the parsing of the printf term.
        with(memoizingBuilder) {
            termStart shouldBe 6
            termEnd shouldBe 9
            param.index shouldBe 1
        }

        // Now visit the parameter and verify the expected callback occurred.
        val param = memoizingBuilder.param
        val memoizingVisitor = MemoizingParameterVisitor()
        param.accept(memoizingVisitor, arrayOf("Answer: ", 42))

        with(memoizingVisitor) {
            value shouldBe 42
            format shouldBe FormatChar.DECIMAL
            options shouldBe WITH_GROUPING
        }
    }

    @Test
    fun testTrailingFormatNotSupportedInBraceFormat() {
        val exception = assertThrows<ParseException> {
            PARSER.parseBraceFormatTerm(null, 0, "{0:x}", 0, 3, 5)
        }
        exception.message shouldContain "[:x]"
    }
}
