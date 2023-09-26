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

package com.google.common.flogger.parser

import io.spine.logging.flogger.backend.FormatChar
import com.google.common.flogger.parser.given.MemoizingMessageBuilder
import com.google.common.flogger.parser.given.MemoizingParameterVisitor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [DefaultPrintfMessageParser].
 *
 * @see <a href="https://github.com/google/flogger/blob/master/api/src/test/java/com/google/common/flogger/parser/DefaultPrintfMessageParserTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`DefaultPrintfMessageParser` should")
internal class DefaultPrintfMessageParserSpec {

    private val parser = DefaultPrintfMessageParser.getInstance()

    @Test
    fun `parse printf-based format string`() {
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
    fun `fail on unknown printf format`() {
        val exception = assertThrows<ParseException> {
            parser.parsePrintfTerm(null, 0, "%Q", 0, 1, 1)
        }
        exception.message shouldContain "[%Q]"
    }

    @Test
    fun `fail on invalid printf flag`() {
        val exception = assertThrows<ParseException> {
            parser.parsePrintfTerm(null, 0, "%0s", 0, 1, 2)
        }
        exception.message shouldContain "[%0s]"
    }
}
