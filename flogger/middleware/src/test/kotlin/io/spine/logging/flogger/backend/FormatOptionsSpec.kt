/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.flogger.backend

import io.spine.logging.flogger.backend.FormatOptions.ALL_FLAGS
import io.spine.logging.flogger.backend.FormatOptions.FLAG_LEFT_ALIGN
import io.spine.logging.flogger.backend.FormatOptions.FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES
import io.spine.logging.flogger.backend.FormatOptions.FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES
import io.spine.logging.flogger.backend.FormatOptions.FLAG_SHOW_ALT_FORM
import io.spine.logging.flogger.backend.FormatOptions.FLAG_SHOW_GROUPING
import io.spine.logging.flogger.backend.FormatOptions.FLAG_SHOW_LEADING_ZEROS
import io.spine.logging.flogger.backend.FormatOptions.FLAG_UPPER_CASE
import io.spine.logging.flogger.backend.FormatOptions.UNSET
import io.spine.logging.flogger.backend.FormatOptions.parse
import io.spine.logging.flogger.parser.ParseException
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [FormatOptions].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/FormatOptionsTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`FormatOptions` should")
internal class FormatOptionsSpec {

    private val defaultOptions = FormatOptions.getDefault()

    @Test
    fun `provide default options`() {
        defaultOptions shouldBeSameInstanceAs FormatOptions.getDefault()
        defaultOptions.width shouldBe UNSET
        defaultOptions.precision shouldBe UNSET
        defaultOptions.flags shouldBe 0
    }

    @Test
    fun `parse default options`() {
        val options = parse("%x", 1, 1, false)
        options shouldBeSameInstanceAs defaultOptions

        // Upper-case options are different from the default, but are the same
        // if the case is filtered out.
        val upperOptions = parse("%X", 1, 1, true)
        upperOptions shouldNotBeSameInstanceAs defaultOptions
    }

    @Test
    fun `parse multiple flags`() {
        val options = parse("%-0#+ (,x", 1, 8, true)
        options.width shouldBe UNSET
        options.precision shouldBe UNSET
        options.flags shouldBe ALL_FLAGS
    }

    @Test
    fun `fail on parsing bad flags`() {
        shouldThrow<ParseException> {
            parse("%", 0, 1, false) // Not a flag.
        }
        shouldThrow<ParseException> {
            parse("-#-", 0, 3, false) // Duplicate flags.
        }
    }

    @Test
    fun `parse width`() {
        val options = parse("%1234x", 1, 5, false)
        options.width shouldBe 1234
        options.precision shouldBe UNSET
        options.flags shouldBe 0
    }

    @Test
    fun `fail on too large width`() {
        val maxAllowedWidth = 999999
        shouldNotThrow<ParseException> {
            parse("%${maxAllowedWidth}x", 1, 7, false)
        }
        shouldThrow<ParseException> {
            parse("%${maxAllowedWidth + 1}x", 1, 8, false)
        }
    }

    @Test
    fun `parse precision`() {
        val options = parse("%.1234x", 1, 6, false)
        options.width shouldBe UNSET
        options.precision shouldBe 1234
        options.flags shouldBe 0
    }

    @Test
    fun `fail on too large precision`() {
        val maxAllowedPrecision = 999999
        shouldNotThrow<ParseException> {
            parse("%.${maxAllowedPrecision}x", 1, 8, false)
        }
        shouldThrow<ParseException> {
            parse("%.${maxAllowedPrecision + 1}x", 2, 9, false)
        }
    }

    @Test
    fun `validate accordingly to the allowed criteria`() {
        val options = parse("-#,123.456", false)
        val givenFlags = FLAG_LEFT_ALIGN or FLAG_SHOW_ALT_FORM or FLAG_SHOW_GROUPING

        // Allow all flags and precision (should always return true).
        options.validate(ALL_FLAGS, true).shouldBeTrue()
        // Still ok if limit allowed flags to those present.
        options.validate(givenFlags, true).shouldBeTrue()

        // Fails if disallow precision.
        options.validate(givenFlags, false).shouldBeFalse()
        // Fails if disallow one given flag.
        options.validate(givenFlags xor FLAG_SHOW_GROUPING, true).shouldBeFalse()
    }

    @Test
    fun `validate like general type`() {
        val options = parse("-123", 0, 4, false)
        options.areValidFor(FormatChar.FLOAT).shouldBeTrue()
        options.areValidFor(FormatChar.DECIMAL).shouldBeTrue()
        options.areValidFor(FormatChar.OCTAL).shouldBeTrue()
        options.areValidFor(FormatChar.STRING).shouldBeTrue()
    }

    @Test
    fun `validate like floating point`() {
        val options = parse("-,123.456", false)
        options.areValidFor(FormatChar.FLOAT).shouldBeTrue()

        // Decimal does not permit precision.
        options.areValidFor(FormatChar.DECIMAL).shouldBeFalse()
        // Octal does not permit grouping or negative flags.
        options.areValidFor(FormatChar.OCTAL).shouldBeFalse()
        // String is not a numeric type.
        options.areValidFor(FormatChar.STRING).shouldBeFalse()
    }

    @Test
    fun `validate like decimal`() {
        val options = parse("-,123", false)
        options.areValidFor(FormatChar.FLOAT).shouldBeTrue()
        options.areValidFor(FormatChar.DECIMAL).shouldBeTrue()

        // Octal does not permit grouping or negative flags.
        options.areValidFor(FormatChar.OCTAL).shouldBeFalse()
        // String is not a numeric type.
        options.areValidFor(FormatChar.STRING).shouldBeFalse()
    }

    @Test
    fun `reject inconsistent flags`() {
        // Prefixing plus and space for negative values is always incompatible for all formats.
        var options = parse("+ ", false)
        for (fc in FormatChar.values()) {
            options.areValidFor(fc).shouldBeFalse()
        }

        // Left alignment and zero padding are always incompatible for all formats.
        options = parse("-0", false)
        for (fc in FormatChar.values()) {
            options.areValidFor(fc).shouldBeFalse()
        }
    }

    @Test
    fun `always validate by default`() {
        for (fc in FormatChar.values()) {
            defaultOptions.areValidFor(fc).shouldBeTrue()
        }
    }

    @Test
    fun `append printf options`() {
        val out = StringBuilder()
        val options = parse("+-( #0,123.456", false)
        options.appendPrintfOptions(out)
        "$out" shouldBe " #(+,-0123.456"
    }

    @Test
    fun `spawn another options based on the given criteria`() {
        defaultOptions.filter(ALL_FLAGS, true, true).isDefault.shouldBeTrue()

        val options = parse("+- #0,123.456", true)
        options.filter(0, false, false).isDefault.shouldBeTrue()
        options.filter(ALL_FLAGS, true, true) shouldBeSameInstanceAs options

        val flags = FLAG_LEFT_ALIGN or FLAG_SHOW_ALT_FORM or
                FLAG_SHOW_GROUPING or FLAG_SHOW_LEADING_ZEROS
        var filtered = options.filter(flags, true, false)
        filtered.shouldLeftAlign() shouldBe true
        filtered.shouldShowAltForm() shouldBe true
        filtered.shouldShowGrouping() shouldBe true
        filtered.shouldShowLeadingZeros() shouldBe true

        filtered.shouldPrefixSpaceForPositiveValues() shouldBe false
        filtered.shouldPrefixPlusForPositiveValues() shouldBe false

        filtered.width shouldBe 123
        filtered.precision shouldBe UNSET
        filtered.shouldUpperCase() shouldBe false

        // Flags incompatible with the first set.
        val otherFlags = FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES or
                FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES or FLAG_UPPER_CASE
        filtered = options.filter(otherFlags, false, true)
        filtered.shouldLeftAlign() shouldBe false
        filtered.shouldShowAltForm() shouldBe false
        filtered.shouldShowGrouping() shouldBe false
        filtered.shouldShowLeadingZeros() shouldBe false
        filtered.shouldPrefixSpaceForPositiveValues() shouldBe true
        filtered.shouldPrefixPlusForPositiveValues() shouldBe true

        filtered.width shouldBe UNSET
        filtered.precision shouldBe 456
        filtered.shouldUpperCase() shouldBe true
    }

    @Test
    fun `be comparable`() {
        val options = parse("+-( #0,123.456", false)
        // Not the same as the default options.
        options shouldNotBe defaultOptions
        // Order of flags doesn't matter.
        options shouldBe parse(",0# (-+123.456", false)
        // Different flags matter.
        options shouldNotBe parse("123.456", false)
        // Different width matters.
        options shouldNotBe parse(",0# (-+999.456", false)
        // Different precision matters.
        options shouldNotBe parse(",0# (-+123.999", false)
        // Upper-case flag does matter.
        options shouldNotBe parse(",0# (-+123.456", true)

        options.hashCode() shouldNotBe defaultOptions.hashCode()
        options.hashCode() shouldBe parse("+-( #0,123.456", false).hashCode()
    }
}

private fun parse(str: String, isUpperCase: Boolean) = parse(str, 0, str.length, isUpperCase)
