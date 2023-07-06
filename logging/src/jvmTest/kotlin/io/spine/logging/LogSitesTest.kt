package io.spine.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.spine.logging.given.MyLoggingHelper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogSites` should")
internal class LogSitesTest {

    /**
     * Tests a typical use case, in which a logging helper uses [LogSites.callerOf]
     * to determine who called the helper.
     */
    @Test
    fun `find a call site of the given class`() {
        val consoleOutput = tapConsole {
            MyLoggingHelper.logWithForwardedLogSite { "test log message" }
        }
        consoleOutput shouldContain LogSitesTest::class.simpleName!!
        consoleOutput shouldNotContain MyLoggingHelper::class.simpleName!!
        println(consoleOutput)
    }

    @Test
    fun `find a call site of the current invocation`() {
        val logSite = LogSites.logSite()
        val stackTrace = (java.lang.Exception() as Throwable).stackTrace
        val callSite = stackTrace[0]
        val expectedLogSite = InjectedLogSite(
            callSite.className,
            callSite.methodName,
            callSite.lineNumber - 1
        )
        logSite shouldBe expectedLogSite
    }
}
