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

import io.spine.logging.StackSize
import io.spine.logging.StackTraceElement
import java.io.Serial

/**
 * A synthetic exception which can be attached to log statements when additional stack trace
 * information is required in log files or via tools such as ECatcher.
 *
 * The name of this class may become relied upon implicitly by tools such as ECatcher.
 * Do not rename or move this class without checking for implicit in logging tools.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogSiteStackTrace.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@Suppress("ExceptionClassNameDoesntEndWithException")
public class LogSiteStackTrace(
    cause: Throwable?,
    stackSize: StackSize,
    syntheticStackTrace: Array<out StackTraceElement?>
) : Exception(stackSize.toString(), cause) {

    public companion object {
        @Serial
        private const val serialVersionUID: Long = 0L
    }

    init {
        /*
         * This takes a defensive copy, but there's no way around that.
         * Note that we cannot override `getStackTrace()` to avoid a defensive copy because
         * that breaks stack trace formatting (which doesn't call `getStackTrace()` directly).
         * See b/27310448.
         */
        setStackTrace(syntheticStackTrace)
    }

    /**
     * We override this because it gets called from the superclass constructor, and
     * we don't want it to do any work (we always replace it immediately).
     */
    @Suppress("NonSynchronizedMethodOverridesSynchronizedMethod")
    override fun fillInStackTrace(): Throwable = this
}
