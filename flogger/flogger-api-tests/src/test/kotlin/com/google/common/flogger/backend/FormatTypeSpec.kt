/*
 * Copyright (C) 2015 The Flogger Authors.
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

package com.google.common.flogger.backend


import com.google.common.flogger.backend.FormatType.GENERAL
import com.google.common.flogger.backend.FormatType.BOOLEAN
import com.google.common.flogger.backend.FormatType.CHARACTER
import com.google.common.flogger.backend.FormatType.INTEGRAL
import com.google.common.flogger.backend.FormatType.FLOAT
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
