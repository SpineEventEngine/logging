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

package io.spine.logging.flogger.given

import io.spine.logging.flogger.backend.FloggerLogData
import io.spine.logging.flogger.backend.LoggerBackend
import com.google.common.flogger.testing.FakeLoggerBackend
import java.util.*
import java.util.logging.Level

/**
 * A simple logging backend for tests.
 *
 * Differs from [FakeLoggerBackend] in that it actually formats the given
 * [FloggerLogData] using built-in Java formatting. See [log] method for details.
 */
internal open class MemoizingBackend : LoggerBackend() {

    val logged: MutableList<String?> = ArrayList()

    override fun getLoggerName(): String = "<unused>"

    override fun isLoggable(lvl: Level): Boolean = true

    /**
     * Format without using Flogger util classes, so we can test what happens
     * if arguments cause errors.
     *
     * The core utility classes handle this properly. But custom backends
     * are not obligated to use them.
     */
    override fun log(data: FloggerLogData) {
        val templateContext = data.templateContext
        if (templateContext == null) {
            logged.add("${data.getLiteralArgument()}")
        }
        val formatted = templateContext.message.format(Locale.ENGLISH, *data.arguments)
        logged.add(formatted)
    }

    // Don't handle any errors in the backend, so we can test “last resort” error handling.
    override fun handleError(error: RuntimeException, badData: FloggerLogData) = throw error
}
