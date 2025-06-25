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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.backend.FormatOptions.FLAG_SHOW_GROUPING
import io.spine.logging.jvm.backend.FormatOptions.UNSET
import io.spine.logging.jvm.parser.given.MemoizingMessageBuilder
import io.spine.logging.jvm.parser.given.MemoizingParameterVisitor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [DefaultBraceStyleMessageParser].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/parser/DefaultBraceStyleMessageParserTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`DefaultBraceStyleMessageParser` should")
internal class DefaultBraceStyleMessageParserSpec {

    companion object {
        private val PARSER = DefaultBraceStyleMessageParser.getInstance()
        private val WITH_GROUPING = FormatOptions.of(FLAG_SHOW_GROUPING, UNSET, UNSET)
    }

    @Test
    fun `parse brace-based format string`() {
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
        param.accept(memoizingVisitor, arrayOf<Any>("Answer: ", 42))

        with(memoizingVisitor) {
            value shouldBe 42
            format shouldBe FormatChar.DECIMAL
            options shouldBe WITH_GROUPING
        }
    }

    @Test
    fun `fail on trailing format specifiers`() {
        val exception = assertThrows<ParseException> {
            PARSER.parseBraceFormatTerm(null, 0, "{0:x}", 0, 3, 5)
        }
        exception.message shouldContain "[:x]"
    }
}
