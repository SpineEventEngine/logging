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

package io.spine.logging.backend.system

import io.spine.logging.backend.system.given.TestAbstractRecord
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [AbstractJulLogRecord].
 *
 * @see <a href="https://github.com/google/flogger/blob/70c5aea863952ee61b3d33afb41f2841b6d63455/api/src/test/java/com/google/common/flogger/backend/system/AbstractLogRecordTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`AbstractLogRecord` should")
internal class AbstractJulLogRecordSpec {

    private val literal = "Hello %s"
    private val argument = "World"
    private val expectedMessage = "Copied: Hello World"
    private val expectedAppended = "Appended: Hello World"

    @Test
    fun `cache the returned message`() {
        val record = TestAbstractRecord(literal, argument)
        val message = record.message
        message shouldBe expectedMessage
        record.message shouldBeSameInstanceAs message
    }

    @Test
    fun `cache the returned formatted message`() {
        val mutableArgument = StringBuilder(argument)
        val record = TestAbstractRecord(literal, mutableArgument)
        val formatted = record.formattedMessage
        formatted shouldBe expectedMessage
        record.formattedMessage shouldBeSameInstanceAs formatted
    }

    @Test
    fun `override the initially supplied message`() {
        val record = TestAbstractRecord(literal, argument)
        record.message shouldBe expectedMessage
        val overriddenMessage = "Custom"
        record.message = overriddenMessage
        record.message shouldBe overriddenMessage
        record.message shouldBeSameInstanceAs overriddenMessage
        record.parameters.shouldBeEmpty()
    }

    @Test
    fun `override with parameters`() {
        val record = TestAbstractRecord(literal, argument)
        record.message shouldBe expectedMessage
        record.parameters.shouldBeEmpty()

        // Please note, braces are NOT printf formatting options.
        // We pass parameters for braces and arguments for printf options.
        val overriddenMessage = "Custom {0}"
        record.message = overriddenMessage

        // Without parameters, the placeholders are not processed.
        "${record.appendFormattedMessageTo(StringBuilder())}" shouldBe overriddenMessage
        record.formattedMessage shouldBe overriddenMessage

        val parameter = "Parameter"
        record.parameters = arrayOf(parameter)
        record.parameters.shouldContainExactly(parameter)

        // With parameters, the placeholders are substituted.
        val expectedMessage = "Custom Parameter"
        "${record.appendFormattedMessageTo(StringBuilder())}" shouldBe expectedMessage
        record.formattedMessage shouldBe expectedMessage
    }

    @Test
    fun `append formatted messages to a buffer without caching`() {

        // By default, `AbstractLogRecord` doesn't cache the message
        // until `AbstractLogRecord.getMessage()` is called.

        val mutableArgument = StringBuilder(argument)
        val record = TestAbstractRecord(literal, mutableArgument)
        "${record.appendFormattedMessageTo(StringBuilder())}" shouldBe expectedAppended

        // Since the message is not cached, it is still can be modified
        // through the passed mutable argument.
        val insertion = "Mutable"
        val expectedAppended = expectedAppended.replace(argument, "$insertion$argument")
        mutableArgument.insert(0, insertion)
        record.appendFormattedMessageTo(StringBuilder()).toString() shouldBe expectedAppended
    }

    @Test
    fun `append formatted message to a buffer with caching`() {
        val mutableArgument = StringBuilder(argument)
        val record = TestAbstractRecord(literal, mutableArgument)
        "${record.appendFormattedMessageTo(StringBuilder())}" shouldBe expectedAppended

        // After a call to `AbstractLogRecord.getMessage()`, the message is cached.
        // Updating of a mutable argument has no effect, and the underlying
        // formatter is not called.
        record.message
        mutableArgument.insert(0, "IGNORED")
        "${record.appendFormattedMessageTo(StringBuilder())}" shouldBe expectedMessage
    }
}
