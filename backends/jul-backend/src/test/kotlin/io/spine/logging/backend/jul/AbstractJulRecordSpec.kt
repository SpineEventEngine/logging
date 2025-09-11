/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.jul

import io.spine.logging.backend.jul.given.StubJulRecord
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [AbstractJulRecord].
 *
 * @see <a href="https://rb.gy/ifbj5">Original Java code</a> for historical context.
 */
@DisplayName("`AbstractJulRecord` should")
internal class AbstractJulRecordSpec {

    private val literal = "Hello %s"
    private val argument = "World"
    private val expectedMessage = "Copied: Hello World"

    @Test
    fun `cache the returned message`() {
        val record = StubJulRecord(literal, argument)
        val message = record.message
        message shouldBe expectedMessage
        record.message shouldBeSameInstanceAs message
    }

    @Test
    fun `override the initially supplied message`() {
        val record = StubJulRecord(literal, argument)
        record.message shouldBe expectedMessage
        val overriddenMessage = "Custom"
        record.message = overriddenMessage
        record.message shouldBe overriddenMessage
        record.message shouldBeSameInstanceAs overriddenMessage
        record.parameters.shouldBeEmpty()
    }
}
