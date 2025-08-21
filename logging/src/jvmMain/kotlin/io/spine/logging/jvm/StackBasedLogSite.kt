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

package io.spine.logging.jvm

import io.spine.logging.LogSite
import io.spine.logging.StackTraceElement
import kotlin.math.max

/**
 * A stack-based log site which uses information from a given [StackTraceElement].
 *
 * Unlike truly unique injected log sites, `StackBasedLogSite` falls back to using
 * the class name, method name and line number for `equals()` and `hashCode()`.
 * This makes it almost as good as a globally unique instance in most cases,
 * except if either of the following is true:
 *
 * - There are two log statements on a single line.
 * - Line number information is stripped from the class file.
 *
 * This class should not be used directly outside the core Logging libraries.
 * If you need to generate a [LogSite] from a [StackTraceElement], use
 * `LogSite.logSiteFrom(StackTraceElement)` for proper log site creation.
 *
 * @property element The stack trace element to use for the log site.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/StackBasedLogSite.java">
 *    Original Java code of Google Flogger</a> for historical context.
 */
internal class StackBasedLogSite(
    private val element: StackTraceElement
) : LogSite() {

    override val className: String
        get() = element.className

    override val methodName: String
        get() = element.methodName

    /**
     * Prohibits negative numbers (which can appear in stack trace elements) from being returned.
     */
    override val lineNumber: Int = max(element.lineNumber, UNKNOWN_LINE)

    override val fileName: String?
        get() = element.fileName

    override fun equals(other: Any?): Boolean =
        (other is StackBasedLogSite) && element == other.element

    /**
     * Returns the hash code of the stack trace element.
     *
     * ### Implementation note
     *
     * Note that (unlike other log site implementations) this
     * hash-code appears to include the file name when creating a hashcode.
     * But this should be the same every time a stack trace element is created,
     * so it shouldn't be a problem.
     */
    override fun hashCode(): Int = element.hashCode()
}
