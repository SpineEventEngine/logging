/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend

import io.spine.logging.jvm.backend.FormatType.GENERAL
import io.spine.logging.jvm.backend.FormatType.BOOLEAN
import io.spine.logging.jvm.backend.FormatType.CHARACTER
import io.spine.logging.jvm.backend.FormatType.INTEGRAL
import io.spine.logging.jvm.backend.FormatType.FLOAT
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [FormatType].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/FormatTypeTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`FormatType` should")
internal class FormatTypeSpec {

    companion object {
        private val ANY_OBJECT = Any()
    }

    @Test
    fun `format any object`() {
        GENERAL shouldFormat  ANY_OBJECT
        GENERAL shouldFormat  "any string"
        GENERAL.shouldNotBeNumeric()
    }

    @Test
    fun `format boolean`() {
        BOOLEAN shouldFormat  true
        BOOLEAN shouldFormat  false

        BOOLEAN shouldNotFormat ANY_OBJECT
        BOOLEAN shouldNotFormat "any string"
        BOOLEAN.shouldNotBeNumeric()
    }

    @Test
    fun `format character`() {
        CHARACTER shouldFormat  'a'
        CHARACTER shouldFormat  0.toByte()
        CHARACTER shouldFormat  0
        CHARACTER shouldFormat  Character.MAX_CODE_POINT

        CHARACTER shouldNotFormat 0L
        CHARACTER shouldNotFormat BigInteger.ZERO
        CHARACTER shouldNotFormat Character.MAX_CODE_POINT + 1
        CHARACTER shouldNotFormat false
        CHARACTER shouldNotFormat ANY_OBJECT
        CHARACTER shouldNotFormat "any string"
        CHARACTER.shouldNotBeNumeric()
    }

    @Test
    fun `format integral`() {
        INTEGRAL shouldFormat  10
        INTEGRAL shouldFormat  10L
        INTEGRAL shouldFormat  BigInteger.TEN
        INTEGRAL.shouldBeNumeric()

        INTEGRAL shouldNotFormat 10.0F
        INTEGRAL shouldNotFormat 10.0
        INTEGRAL shouldNotFormat BigDecimal.TEN
        INTEGRAL shouldNotFormat 'a'
        INTEGRAL shouldNotFormat false
        INTEGRAL shouldNotFormat ANY_OBJECT
        INTEGRAL shouldNotFormat "any string"
    }

    @Test
    fun `format float`() {
        FLOAT shouldFormat  10.0F
        FLOAT shouldFormat  10.0
        FLOAT shouldFormat  BigDecimal.TEN
        FLOAT.shouldBeNumeric()

        FLOAT shouldNotFormat 10
        FLOAT shouldNotFormat 10L
        FLOAT shouldNotFormat BigInteger.TEN
        FLOAT shouldNotFormat 'a'
        FLOAT shouldNotFormat false
        FLOAT shouldNotFormat ANY_OBJECT
        FLOAT shouldNotFormat "any string"
    }
}

private infix fun FormatType.shouldFormat(any: Any) = canFormat(any).shouldBeTrue()

private infix fun FormatType.shouldNotFormat(any: Any) = canFormat(any).shouldBeFalse()

private fun FormatType.shouldNotBeNumeric() = isNumeric.shouldBeFalse()

private fun FormatType.shouldBeNumeric() = isNumeric.shouldBeTrue()
