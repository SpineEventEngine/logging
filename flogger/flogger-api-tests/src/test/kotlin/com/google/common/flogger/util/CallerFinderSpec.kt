/*
 * Copyright (C) 2013 The Flogger Authors.
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

package com.google.common.flogger.util

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CallerFinder` should")
internal class CallerFinderSpec {

    /**
     * A sanity check if we ever discover a platform where the class name
     * in the stack trace does not match [Class.getName] – this is never quite
     * guaranteed by the JavaDoc in the JDK but is relied upon during log site analysis.
     */
    @Test
    fun `use the class name that matches one in the stack trace`() {
        // Simple case for a top-level named class.
        Throwable().stackTrace[0].className shouldBe CallerFinderSpec::class.java.name

        // Anonymous inner class.
        val obj = object {
            override fun toString(): String {
                return Throwable().stackTrace[0].className
            }
        }

        "$obj" shouldBe obj::class.java.name
    }

    @Test
    fun `find the stack trace element of the immediate caller of the specified class`() {
        // There are 2 internal methods (not including the log method itself)
        // in our fake library.
        val library = LoggerCode(skipCount = 2)
        val code = UserCode(library)
        code.invokeUserCode()
        library.caller shouldNotBe null
        library.caller!!.className shouldBe UserCode::class.java.name
        library.caller!!.methodName shouldBe "loggingMethod"
    }

    @Test
    fun `return 'null' due to wrong skip count`() {
        // If the minimum offset exceeds the number of internal methods, the find fails.
        val library = LoggerCode(skipCount = 3)
        val code = UserCode(library)
        code.invokeUserCode()
        library.caller shouldBe null
    }
}

/**
 * Fake class that emulates some code calling a log method.
 */
private class UserCode(private val logger: LoggerCode) {

    fun invokeUserCode() {
        loggingMethod()
    }

    private fun loggingMethod() {
        logger.logMethod()
    }
}

/**
 * A fake class that emulates the logging library,
 * which eventually calls [CallerFinder.findCallerOf].
 */
private class LoggerCode(private val skipCount: Int) {

    var caller: StackTraceElement? = null

    fun logMethod() {
        internalMethodOne()
    }

    private fun internalMethodOne() {
        internalMethodTwo()
    }

    private fun internalMethodTwo() {
        caller = CallerFinder.findCallerOf(LoggerCode::class.java, skipCount)
    }
}
