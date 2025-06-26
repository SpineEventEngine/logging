/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend.given

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.jvm.JvmLogSite
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.LogContext
import io.spine.logging.jvm.backend.LogData
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.backend.TemplateContext
import io.spine.logging.jvm.given.FakeLogSite
import io.spine.logging.jvm.parser.DefaultBraceStyleMessageParser
import io.spine.logging.jvm.parser.DefaultPrintfMessageParser
import io.spine.logging.jvm.parser.MessageParser
import java.util.logging.Level

/**
 * A mutable [LogData] fot testing backends and other log handling code.
 *
 * @see <a href="http://rb.gy/z2i0q">Original Java code of Google Flogger</a> for historical context.
 */
@Suppress("TooManyFunctions") // Many getters and setters.
class FakeLogData : LogData {

    private var level = Level.INFO
    private var context: TemplateContext? = null
    private var arguments: Array<out Any?>? = null
    private var literalArgument: Any? = null
    private var timestampNanos = 0L
    private val metadata = FakeMetadata()
    private var logSite: JvmLogSite = LOG_SITE

    companion object {
        private const val LOGGER_NAME = "io.spine.LoggerName"
        private const val LOGGING_CLASS = "io.spine.FakeClass"
        private const val LOGGING_METHOD = "doAct"
        private const val LINE_NUMBER = 123
        private const val SOURCE_FILE = "src/io/spine/FakeClass.java"
        private val LOG_SITE = FakeLogSite(LOGGING_CLASS, LOGGING_METHOD, LINE_NUMBER, SOURCE_FILE)

        /**
         * Creates an instance with a printf-formatted message.
         */
        fun withPrintfStyle(message: String, vararg arguments: Any?): FakeLogData {
            val printfParser = DefaultPrintfMessageParser.getInstance()
            return FakeLogData(printfParser, message, *arguments)
        }

        /**
         * Creates an instance with a brace-formatted message.
         */
        fun withBraceStyle(message: String, vararg arguments: Any?): FakeLogData {
            val braceParser = DefaultBraceStyleMessageParser.getInstance()
            return FakeLogData(braceParser, message, *arguments)
        }
    }

    /**
     * Creates an instance with a single literal argument.
     */
    constructor(literalArgument: Any?) {
        this.literalArgument = literalArgument
    }

    private constructor(parser: MessageParser, message: String, vararg arguments: Any?) {
        context = TemplateContext(parser, message)
        this.arguments = arguments
    }

    @CanIgnoreReturnValue
    fun setTimestampNanos(timestampNanos: Long): FakeLogData {
        this.timestampNanos = timestampNanos
        return this
    }

    @CanIgnoreReturnValue
    fun setLevel(level: Level): FakeLogData {
        this.level = level
        return this
    }

    @CanIgnoreReturnValue
    fun setLogSite(logSite: JvmLogSite): FakeLogData {
        this.logSite = logSite
        return this
    }

    @CanIgnoreReturnValue
    fun <T> addMetadata(key: MetadataKey<T>, value: Any?): FakeLogData {
        metadata.add(key, key.cast(value))
        return this
    }

    override fun getLevel(): Level {
        return level
    }

    override fun getTimestampNanos(): Long {
        return timestampNanos
    }

    override fun getLoggerName(): String {
        return LOGGER_NAME
    }

    override fun getLogSite(): JvmLogSite {
        return logSite
    }

    override fun getMetadata(): Metadata {
        return metadata
    }

    override fun wasForced(): Boolean {
        // Check explicit `TRUE` here because `findValue()` can return `null`.
        // That would fail unboxing.
        return metadata.findValue(LogContext.Key.WAS_FORCED) == true
    }

    override fun getTemplateContext(): TemplateContext? {
        return context
    }

    override fun getArguments(): Array<out Any?> {
        check(context != null) {
            "Cannot get log data's arguments without a context."
        }
        return arguments!!.clone()
    }

    override fun getLiteralArgument(): Any? {
        check(context == null) {
            "Cannot get log data's literal argument if a context exists."
        }
        return literalArgument
    }
}

