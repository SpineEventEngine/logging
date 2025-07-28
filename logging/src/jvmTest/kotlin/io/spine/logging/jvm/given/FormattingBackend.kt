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

package io.spine.logging.jvm.given

import io.spine.logging.jvm.backend.LogData
import io.spine.logging.jvm.backend.LoggerBackend
import java.util.*
import java.util.logging.Level

/**
 * A memoizing backend that formats the given [LogData] on its own
 * using built-in Kotlin formatting.
 *
 * See [log] method for details.
 */
internal open class FormattingBackend : LoggerBackend() {

    private val mutableLogged = mutableListOf<String>()

    /**
     * The captured messages that have been logged by this backend.
     */
    val logged: List<String> get() = mutableLogged

    override val loggerName: String = "<unused>"

    override fun isLoggable(level: Level): Boolean = true

    /**
     * Formats the given [LogData] without using core logging utils,
     * so it is possible to test what happens if arguments cause errors.
     *
     * The core utility classes handle this properly. But custom backends
     * are not obligated to use them.
     */
    override fun log(data: LogData) {
        val templateContext = data.templateContext
        if (templateContext == null) {
            mutableLogged.add("${data.literalArgument}")
        } else {
            val pattern = templateContext.message
            val formatted = pattern.format(Locale.ENGLISH, *data.arguments)
            mutableLogged.add(formatted)
        }
    }

    // Do not handle any errors in the backend, so we can test
    // “last resort” error handling.
    override fun handleError(error: RuntimeException, badData: LogData) = throw error
}
