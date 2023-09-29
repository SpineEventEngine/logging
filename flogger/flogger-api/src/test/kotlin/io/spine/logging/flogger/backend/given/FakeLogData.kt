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

package io.spine.logging.flogger.backend.given

import com.google.common.flogger.testing.FakeLogSite.create
import com.google.common.flogger.testing.FakeMetadata
import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.flogger.FloggerLogSite
import io.spine.logging.flogger.FloggerMetadataKey
import io.spine.logging.flogger.LogContext
import io.spine.logging.flogger.backend.LogData
import io.spine.logging.flogger.backend.Metadata
import io.spine.logging.flogger.backend.TemplateContext
import io.spine.logging.flogger.parser.DefaultBraceStyleMessageParser
import io.spine.logging.flogger.parser.DefaultPrintfMessageParser
import io.spine.logging.flogger.parser.MessageParser
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * A mutable [LogData] fot testing backends and other log handling code.
 *
 * @see <a href="http://rb.gy/z2i0q">Original Java code of Google Flogger</a>
 */
class FakeLogData : LogData {

    private var level = Level.INFO
    private var context: TemplateContext? = null
    private var arguments: Array<out Any?>? = null
    private var literalArgument: Any? = null
    private var timestampNanos = 0L
    private val metadata = FakeMetadata()
    private var logSite = create(FAKE_LOGGING_CLASS, FAKE_LOGGING_METHOD, 123, FAKE_SOURCE_PATH)

    companion object {
        private const val FAKE_LOGGER_NAME = "io.spine.LoggerName"
        private const val FAKE_LOGGING_CLASS = "io.spine.FakeClass"
        private const val FAKE_LOGGING_METHOD = "fakeMethod"
        private const val FAKE_SOURCE_PATH = "src/io/spine/FakeClass.java"

        /**
         * Creates an instance for a log statement with printf style formatting.
         */
        fun withPrintfStyle(message: String, vararg arguments: Any?): FakeLogData {
            val printfParser = DefaultPrintfMessageParser.getInstance()
            return FakeLogData(printfParser, message, *arguments)
        }

        /**
         * Creates an instance for a log statement with brace style formatting.
         */
        fun withBraceStyle(message: String, vararg arguments: Any?): FakeLogData {
            val braceParser = DefaultBraceStyleMessageParser.getInstance()
            return FakeLogData(braceParser, message, *arguments)
        }
    }

    /**
     * Creates a fake `LogData` instance representing a log statement with a single, literal
     * argument.
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
    fun setLogSite(logSite: FloggerLogSite): FakeLogData {
        this.logSite = logSite
        return this
    }

    @CanIgnoreReturnValue
    fun <T> addMetadata(key: FloggerMetadataKey<T>, value: Any?): FakeLogData {
        metadata.add(key, key.cast(value))
        return this
    }

    override fun getLevel(): Level {
        return level
    }

    @Deprecated("Deprecated in Java")
    override fun getTimestampMicros(): Long {
        return TimeUnit.NANOSECONDS.toMicros(timestampNanos)
    }

    override fun getTimestampNanos(): Long {
        return timestampNanos
    }

    override fun getLoggerName(): String {
        return FAKE_LOGGER_NAME
    }

    override fun getLogSite(): FloggerLogSite {
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

