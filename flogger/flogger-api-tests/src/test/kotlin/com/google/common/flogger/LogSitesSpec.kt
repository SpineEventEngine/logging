/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.LogSites.callerOf
import com.google.common.flogger.LogSites.logSite
import com.google.common.flogger.LogSites.logSiteFrom
import com.google.common.flogger.MyLogUtil.callerLogSite
import com.google.common.flogger.MyLogUtil.callerLogSiteWrapped
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogSites` should")
internal class LogSitesSpec {

    @Test
    fun `return log site for the current line of code`() {
        val outerMethod = "return log site for the current line of code"
        logSite().getMethodName() shouldBe outerMethod
    }

    @Test
    fun `return log site for the caller of the specified class`() {
        val outerMethod = "return log site for the caller of the specified class"
        callerLogSite.getMethodName() shouldBe outerMethod
        callerLogSiteWrapped.getMethodName() shouldBe outerMethod
    }

    @Test
    fun `return 'INVALID' log site if the caller not found`() {
        callerOf(String::class.java) shouldBe LogSite.INVALID
    }

    @Test
    fun `detect log site using the given stack trace element`() {
        val element = StackTraceElement("class", "method", "file", 42)
        val logSite = logSiteFrom(element)
        logSite.getClassName() shouldBe element.className
        logSite.getMethodName() shouldBe element.methodName
        logSite.getFileName() shouldBe element.fileName
        logSite.getLineNumber() shouldBe element.lineNumber
    }
}

private object MyLogUtil {
    val callerLogSite: LogSite
        get() = callerOf(MyLogUtil::class.java)
    val callerLogSiteWrapped: LogSite
        get() = callerLogSite
}
