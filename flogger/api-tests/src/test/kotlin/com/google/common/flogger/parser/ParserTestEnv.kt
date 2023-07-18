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

import com.google.common.flogger.backend.FormatOptions
import com.google.common.flogger.backend.TemplateContext
import com.google.common.flogger.parameter.Parameter
import com.google.common.flogger.parameter.ParameterVisitor
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import java.lang.StringBuilder
import org.junit.jupiter.api.assertThrows

/**
 * A fake parameter that collects strings generated by the fake parser. Instances of this are
 * generated by parsers during testing to capture the state of the parse when a parameter is
 * processed.
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

internal class FakeMessageParser : MessageParser() {

    override fun <T : Any?> parseImpl(builder: MessageBuilder<T>?) {
        // No op.
    }

    override fun unescape(out: StringBuilder?, message: String?, start: Int, end: Int) {
        // No op.
    }
}

/**
 * Asserts that for a format message, the given parser will emit fake parameters with the
 * specified detail strings (in the given order).
 */
internal fun assertParse(fakeParser: MessageParser, message: String, vararg terms: String) {
    val builder = FakeMessageBuilder(fakeParser, message)
    val fakeMessage = builder.build()
    fakeMessage shouldContainExactly terms.asList()
}

/**
 * Asserts that for a format message, the given parser will produce a parse error with the
 * given snippet in its message.
 */
internal fun assertParseError(fakeParser: MessageParser, message: String, errorPart: String) {
    val exception = assertThrows<ParseException> {
        val fakeBuilder = FakeMessageBuilder(fakeParser, message)
        fakeBuilder.build()
    }
    exception.message shouldContain errorPart
}
