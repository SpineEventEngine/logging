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

package io.spine.logging.flogger

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainDuplicates
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [StackBasedLogSite].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/StackBasedLogSiteTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`StackBasedLogSite` should")
internal class StackBasedLogSiteSpec {

    companion object {
        private const val CLASS_NAME = "com.example.MyClass\$Foo"
        private const val METHOD_NAME = "myMethod"
        private const val LINE_NUMBER = 1234
        private const val FILE_NAME = "MyClass.java"
    }

    @Test
    fun `expose the given log site parameters`() {
        val element = StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER)
        val logSite = StackBasedLogSite(element)
        logSite.className shouldBe CLASS_NAME
        logSite.methodName shouldBe METHOD_NAME
        logSite.lineNumber shouldBe LINE_NUMBER
        logSite.fileName shouldBe FILE_NAME
        logSite.className shouldBe element.className
        logSite.methodName shouldBe element.methodName
        logSite.lineNumber shouldBe element.lineNumber
        logSite.fileName shouldBe element.fileName
    }

    @Test
    fun `throw when the given stack trace element is 'null'`() {
        shouldThrow<NullPointerException> {
            StackBasedLogSite(null)
        }
    }

    @Test
    fun `handle unknown constituents`() {
        val fileName = null // Can be unknown, represented with `null`.
        val lineNumber = -3 // Can also be unknown, represented with a negative value.
        val logSite = stackBasedLogSite(CLASS_NAME, METHOD_NAME, fileName, lineNumber)
        logSite.fileName.shouldBeNull()
        logSite.lineNumber shouldBe LogSite.UNKNOWN_LINE
    }

    @Test
    fun `provide equality check`() {
        val logSite = stackBasedLogSite(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER)
        val similarLogSite = stackBasedLogSite(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER)

        // This is unfortunate, but there's no way to distinguish two log sites
        // on the same line.
        similarLogSite shouldBe logSite

        val distinctLogSites = listOf(
            logSite,
            stackBasedLogSite("com/example/MyOtherClass", METHOD_NAME, FILE_NAME, LINE_NUMBER),
            stackBasedLogSite(CLASS_NAME, "otherMethod", FILE_NAME, LINE_NUMBER),
            stackBasedLogSite(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER + 1)
        )
        distinctLogSites.shouldNotContainDuplicates()

        val hashCodes = distinctLogSites.map { it.hashCode() }
        hashCodes shouldHaveSize distinctLogSites.size
    }
}

private fun stackBasedLogSite(className: String,
                              methodName: String,
                              fileName: String?,
                              lineNumber: Int): StackBasedLogSite {
    val element = StackTraceElement(className, methodName, fileName, lineNumber)
    val logSite = StackBasedLogSite(element)
    return logSite
}
