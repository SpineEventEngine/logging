/*
 * Copyright (C) 2015 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.google.common.flogger

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainDuplicates
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
