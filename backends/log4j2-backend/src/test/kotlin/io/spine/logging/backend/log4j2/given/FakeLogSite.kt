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

package io.spine.logging.backend.log4j2.given

import io.spine.logging.jvm.JvmLogSite
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * A simplified implementation of [JvmLogSite] for testing.
 *
 * @see <a href="http://rb.gy/wal1a">Original Java code of Google Flogger</a> for historical context.
 */
internal class FakeLogSite(
    override val className: String,
    override val methodName: String,
    override val lineNumber: Int,
    private val sourcePath: String?
) : JvmLogSite() {

    override val fileName: String? = sourcePath

    companion object {
        private const val LOGGING_CLASS = "com.example.ClassName"
        private const val SOURCE_FILE = "com/example/ClassName.java"
        private const val LINE_NUMBER = 124

        private val uid = AtomicInteger()
        private val uniqueMethod = { "doAct" + uid.incrementAndGet() }

        /**
         * Creates a unique instance to be used as a key
         * when testing shared static maps.
         */
        fun unique() = FakeLogSite(LOGGING_CLASS, uniqueMethod(), LINE_NUMBER, SOURCE_FILE)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FakeLogSite) {
            return false
        }
        return className == other.className &&
                methodName == other.methodName &&
                lineNumber == other.lineNumber &&
                sourcePath == other.sourcePath
    }

    override fun hashCode(): Int = Objects.hash(className, methodName, lineNumber, sourcePath)
}
