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

package io.spine.logging.flogger.parser.given

import io.spine.logging.flogger.backend.FormatChar
import io.spine.logging.flogger.backend.FormatOptions
import io.spine.logging.flogger.backend.TemplateContext
import io.spine.logging.flogger.parameter.DateTimeFormat
import io.spine.logging.flogger.parameter.Parameter
import io.spine.logging.flogger.parameter.ParameterVisitor
import io.spine.logging.flogger.parser.MessageBuilder
import io.spine.logging.flogger.parser.MessageParser
import io.spine.logging.flogger.parser.ParseException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import kotlin.properties.Delegates.notNull
import org.junit.jupiter.api.assertThrows

/**
 * This files contains test utils for parse-related tests.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/parser/ParserTestUtil.java">
 *     Original Java code of Google Flogger</a>
 */

/**
 * A fake parameter that collects strings generated by the fake parser.
 *
 * Parsers generate instances of this class during tests to capture
 * the state of the parse when a parameter is processed.
 */
internal class FakeParameter(index: Int, private val details: String) :
    Parameter(FormatOptions.getDefault(), index) {

    override fun accept(visitor: ParameterVisitor, value: Any) =
        throw UnsupportedOperationException("not used in test")

    override fun getFormat(): String = throw UnsupportedOperationException("not used in test")

    override fun toString(): String = if (details.isEmpty()) "$index" else "$index:$details"
}

/**
 * A message builder that returns captured detail strings from the fake parser.
 */
internal class FakeMessageBuilder(fakeParser: MessageParser, message: String) :
    MessageBuilder<List<String>>(TemplateContext(fakeParser, message)) {

    private val details = mutableListOf<String>()

    override fun addParameterImpl(termStart: Int, termEnd: Int, param: Parameter?) {
        details.add(param.toString())
    }

    override fun buildImpl(): List<String> = details
}

/**
 * Asserts that for a format message, the given parser will emit fake
 * parameters with the specified detail strings, and in the given order.
 */
internal fun assertParse(fakeParser: MessageParser, message: String, vararg terms: String) {
    val builder = FakeMessageBuilder(fakeParser, message)
    val fakeMessage = builder.build()
    fakeMessage shouldContainExactly terms.asList()
}

/**
 * Asserts that for a format message, the given parser will produce
 * a parse error with the given snippet in its message.
 */
internal fun assertParseError(fakeParser: MessageParser, message: String, errorPart: String) {
    val exception = assertThrows<ParseException> {
        val fakeBuilder = FakeMessageBuilder(fakeParser, message)
        fakeBuilder.build()
    }
    exception.message shouldContain errorPart
}

/**
 * Remembers arguments of [addParameterImpl] method's last invocation.
 */
internal class MemoizingMessageBuilder(parser: MessageParser) :
    MessageBuilder<List<String>>(TemplateContext(parser, "")) {

    lateinit var param: Parameter
    var termStart by notNull<Int>()
    var termEnd by notNull<Int>()

    override fun addParameterImpl(termStart: Int, termEnd: Int, param: Parameter) {
        this.termStart = termStart
        this.termEnd = termEnd
        this.param = param
    }

    override fun buildImpl(): List<String> = emptyList()
}

/**
 * Remembers arguments of [visit] method's last invocation.
 */
internal class MemoizingParameterVisitor : ParameterVisitor {

    lateinit var value: Any
    lateinit var format: FormatChar
    lateinit var options: FormatOptions

    override fun visit(value: Any, format: FormatChar, options: FormatOptions) {
        this.value = value
        this.format = format
        this.options = options
    }

    override fun visitDateTime(value: Any?, format: DateTimeFormat?, options: FormatOptions?) = Unit

    override fun visitPreformatted(value: Any?, formatted: String?) = Unit

    override fun visitMissing() = Unit

    override fun visitNull() = Unit
}
