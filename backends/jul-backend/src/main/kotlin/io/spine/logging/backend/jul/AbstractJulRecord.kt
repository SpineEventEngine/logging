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

package io.spine.logging.backend.jul

import io.spine.logging.backend.LogData
import io.spine.logging.backend.LogMessageFormatter
import io.spine.logging.backend.Metadata
import io.spine.logging.backend.MetadataProcessor
import io.spine.logging.backend.SimpleMessageFormatter
import io.spine.logging.backend.safeToString
import io.spine.logging.toJavaLogging
import java.io.Serial
import java.time.Instant.ofEpochMilli
import java.util.*
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.logging.Level.WARNING
import java.util.logging.LogRecord

/**
 * Abstract base for `java.util.logging` (JUL) log records.
 *
 * This class supports three distinct modes of operation, depending on the state of the message
 * and/or parameters:
 *
 * ## Non-null message, `null` or empty parameters
 *
 * This state is reached either when [getMessage] is first called, or if an explicit
 * non-null message is set via [setMessage] (without setting any parameters).
 * In this state, the message is considered to be formatted, and just returned
 * via [getMessage].
 *
 * ## Non-null message, non-empty parameters
 *
 * This state is only reached if a user calls both [setMessage] and [setParameters].
 * In this state the message is treated as is it were a brace-format log
 * message, and no formatting is attempted.
 * Any relationship between this value, and the log message
 * implied by the contained [LogData] and [Metadata] is lost.
 *
 * For many reasons it is never a good idea for users to modify unknown [LogRecord]
 * instances, but this does happen occasionally, so this class supports that in a best effort way,
 * but users are always recommended to copy [LogRecord] instances if they need
 * to modify them.
 *
 * ## Corollary
 *
 * Because of the defined states above there are a few small, but necessary, changes to
 * behaviour in this class as compared to the "vanilla" JDK [LogRecord].
 *
 * * Since the "message" field being `null` indicates a private state, calling
 *   `setMessage(null)` from outside this class is equivalent to calling `setMessage("")`,
 *   and will not reset the instance to its initial "unformatted" state.
 *   This is within specification for [LogRecord] since the documentation for
 *   [getMessage] says that a return value of `null` is equivalent to the empty string.
 *
 * * Setting the parameters to `null` from outside this class will reset the parameters to
 *   a static singleton empty array. From outside this class, [getParameters] is never
 *   observed to contain `null`. This is also within specification for [LogRecord].
 *
 * * Setting parameters from outside this class (to any value) will also result in the log
 *   message being formatted and cached (if it hadn't been set already). This is to avoid
 *   situations in which parameters are set, but the underlying message is still `null`.
 *
 * * `ResourceBundles` are not supported by `AbstractLogRecord` and any attempt to
 *   set them is ignored.
 *
 * @see <a href="https://rb.gy/yrrs4">Original Java code</a> for historical context.
 */
@Suppress("HardcodedLineSeparator")
public abstract class AbstractJulRecord : LogRecord {

    private val data: LogData
    private val metadata: MetadataProcessor

    /**
     * Constructs a log record for normal logging without filling in format-specific fields.
     * Subclasses calling this constructor are expected to additionally call [setThrown] and
     * perhaps [setMessage] (depending on whether eager message caching is desired).
     */
    @Suppress("LeakingThis")
    protected constructor(data: LogData, scope: Metadata) : super(
        data.level.toJavaLogging(),
        null
    ) {
        this.data = data
        this.metadata = MetadataProcessor.forScopeAndLogSite(scope, data.metadata)

        val logSite = data.logSite
        val timestampMillis = NANOSECONDS.toMillis(data.timestampNanos)
        sourceClassName = logSite.className
        sourceMethodName = logSite.methodName
        loggerName = data.loggerName
        instant = ofEpochMilli(timestampMillis)

        // Pre-initialize parameters to avoid null-hostile application code changing it.
        super.setParameters(NO_PARAMETERS)
    }

    /**
     * Constructs a log record in response to an exception during a previous logging attempt.
     */
    @Suppress("LeakingThis")
    protected constructor(error: RuntimeException, data: LogData, scope: Metadata) : this(
        data,
        scope
    ) {
        // Re-target this log message as a warning (or above) since it indicates a real bug.
        level = if (data.level.value < WARNING.intValue()) WARNING else data.level.toJavaLogging()
        thrown = error
        val errorMsg = StringBuilder("LOGGING ERROR: ").append(error.message).append('\n')
        safeAppend(data, errorMsg)
        message = errorMsg.toString()
    }

    /**
     * Returns the formatter used when formatting [LogData].
     */
    protected open fun getLogMessageFormatter(): LogMessageFormatter =
        SimpleMessageFormatter.getDefaultFormatter()

    override fun setParameters(parameters: Array<out Any>?) {
        // Cache the internal formatted message if someone indicates
        // they want to change the parameters.
        @Suppress("UNUSED_VARIABLE", "unused")
        val unused = message
        val nonNull = parameters ?: NO_PARAMETERS
        super.setParameters(nonNull)
    }

    override fun setMessage(message: String?) {
        super.setMessage(message ?: "")
    }

    override fun getMessage(): String {
        val cached = super.getMessage()
        if (cached != null) return cached
        val formatted = getLogMessageFormatter().format(data, metadata)
        super.setMessage(formatted)
        return formatted
    }

    /** No-op. */
    override fun setResourceBundle(bundle: ResourceBundle?): Unit = Unit

    /** No-op. */
    override fun setResourceBundleName(name: String?): Unit = Unit

    /**
     * Returns the [LogData] instance encapsulating the current fluent log statement.
     */
    public fun getLogData(): LogData = data

    /**
     * Returns the immutable [MetadataProcessor] which provides a unified view of scope and log site metadata.
     */
    public fun getMetadataProcessor(): MetadataProcessor = metadata

    @Suppress("SpreadOperator")
    override fun toString(): String {
        val out = StringBuilder()
        out.append(javaClass.simpleName)
            .append(" {\n  message: ")
            .append(message)
            .append("\n  arguments: ")
            .append(if (parameters != null) listOf(*parameters) else "<none>")
            .append('\n')
        safeAppend(getLogData(), out)
        out.append("\n}")
        return out.toString()
    }

    private companion object {
        @Serial
        private const val serialVersionUID: Long = 0L

        private val NO_PARAMETERS: Array<Any?> = arrayOf()

        private fun safeAppend(data: LogData, out: StringBuilder) {
            out.append("  original message: ")
            out.append(data.literalArgument.safeToString())
            val metadata = data.metadata
            if (metadata.size() > 0) {
                out.append("\n  metadata:")
                for (n in 0 until metadata.size()) {
                    out.append("\n    ")
                        .append(metadata.getKey(n).label)
                        .append(": ")
                        .append(metadata.getValue(n).safeToString())
                }
            }
            out.append("\n  level: ")
                .append(data.level.safeToString())
            out.append("\n  timestamp (nanos): ")
                .append(data.timestampNanos)
            out.append("\n  class: ")
                .append(data.logSite.className)
            out.append("\n  method: ")
                .append(data.logSite.methodName)
            out.append("\n  line number: ")
                .append(data.logSite.lineNumber)
        }
    }
}
