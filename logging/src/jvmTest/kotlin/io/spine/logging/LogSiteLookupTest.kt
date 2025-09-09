package io.spine.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.spine.logging.LogSiteLookup.logSite
import io.spine.logging.given.MyLoggingHelper
import io.spine.logging.testing.tapConsole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogSiteLookup` should")
internal class LogSiteLookupTest {

    /**
     * Tests a typical use case, in which a logging helper uses
     * [LogSiteLookup.callerOf] to determine who called the helper.
     */
    @Test
    fun `find a call site of the given class`() {
        val consoleOutput = tapConsole {
            MyLoggingHelper.logWithForwardedLogSite { "test log message" }
        }
        consoleOutput shouldContain LogSiteLookupTest::class.simpleName!!
        consoleOutput shouldNotContain MyLoggingHelper::class.simpleName!!
    }

    @Test
    @LogSiteInjector
    fun `find a call site of the current invocation`() {
        val logSite = logSite()
        val stackTrace = (java.lang.Exception() as Throwable).stackTrace
        val callSite = stackTrace[0]
        val expectedLogSite = injectedLogSite(
            callSite.className,
            callSite.methodName,
            callSite.lineNumber - 1,
            null,
        )
        logSite shouldBe expectedLogSite
    }
}
