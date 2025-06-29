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

package io.spine.logging.jvm.backend

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.MetadataKey.KeyValueHandler
import io.spine.logging.jvm.LogContext
import java.util.Collections

/**
 * Helper class for formatting LogData as text. This class is useful for any logging backend which
 * performs unstructured, text only, logging. Note however that it makes several assumptions
 * regarding metadata and formatting, which may not apply to every text based logging backend.
 *
 * This primarily exists to support both the JDK logging classes and text only Android backends.
 * Code in here may be factored out as necessary to support other use cases in future.
 *
 * If a text based logger backend is not performance critical, then it should just append the log
 * message and metadata to a local buffer. For example:
 *
 * ```kotlin
 * val metadata =
 *     MetadataProcessor.forScopeAndLogSite(Platform.getInjectedMetadata(), logData.metadata)
 * val buffer = StringBuilder()
 * // Optional prefix goes here...
 * SimpleMessageFormatter.getDefaultFormatter().append(logData, metadata, buffer)
 * // Optional suffix goes here...
 * val message = buffer.toString()
 * ```
 *
 * If additional metadata keys, other than the `cause` are to be omitted, then
 * [getSimpleFormatterIgnoring] can be used to obtain a static formatter,
 * instead of using the default.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/SimpleMessageFormatter.java">
 *         Original Java code of Google Flogger</a> for historical context.
 */
public object SimpleMessageFormatter {

    @Suppress("ConstantCaseForConstants")
    private val DEFAULT_KEYS_TO_IGNORE: Set<MetadataKey<*>> =
        Collections.singleton(LogContext.Key.LOG_CAUSE)

    private val DEFAULT_FORMATTER: LogMessageFormatter = newFormatter(DEFAULT_KEYS_TO_IGNORE)

    /**
     * Returns the singleton default log message formatter.
     *
     * This formats log messages in the form:
     * ```
     * Log message [CONTEXT key="value" id=42 ]
     * ```
     * with context from the log data and scope, merged together in a sequence of key/value
     * pairs after the formatted message.
     *
     * If the log message is long or multi-line, then the context suffix will
     * be formatted on a single separate line.
     *
     * The `cause` is omitted from the context section, since it is handled separately by
     * most logger backends and not considered part of the formatted message.
     * Other internal metadata keys may also be suppressed.
     */
    @JvmStatic
    public fun getDefaultFormatter(): LogMessageFormatter = DEFAULT_FORMATTER

    /**
     * Returns a log message formatter which formats log messages in the form:
     * ```
     * Log message [CONTEXT key="value" id=42 ]
     * ```
     * with context from the log data and scope, merged together in a sequence of key/value
     * pairs after the formatted message.
     *
     * If the log message is long or multi-line, then the context
     * suffix will be formatted on a single separate line.
     *
     * This differs from the default formatter because it allows the caller to specify
     * additional metadata keys to be omitted from the formatted context.
     *
     * By default, the `cause` is always omitted from the context section, since it is handled
     * separately by most logger backends and almost never expected to be part of
     * the formatted message. Other internal metadata keys may also be suppressed.
     */
    public fun getSimpleFormatterIgnoring(
        vararg extraIgnoredKeys: MetadataKey<*>
    ): LogMessageFormatter {
        if (extraIgnoredKeys.isEmpty()) {
            return getDefaultFormatter()
        }
        val ignored = DEFAULT_KEYS_TO_IGNORE.toMutableSet()
        ignored.addAll(extraIgnoredKeys)
        return newFormatter(ignored)
    }

    /**
     * Appends formatted context information to the given buffer using
     * the supplied metadata handler.
     *
     * A custom metadata handler is useful if the logger backend wishes to:
     *
     * - Ignore more than just the default set of metadata keys (currently just the "cause").
     * - Intercept and capture metadata values for additional processing or logging control.
     *
     * @param metadataProcessor A snapshot of the metadata to be processed ([MetadataProcessor] is
     *        reusable so passing one in can save repeated processing of the same metadata).
     * @param metadataHandler A metadata handler for intercepting and dispatching
     *        metadata during formatting.
     * @param buffer The destination buffer into which the log message and
     *        metadata will be appended.
     *
     * @return the given destination buffer (for method chaining).
     */
    @CanIgnoreReturnValue
    public fun appendContext(
        metadataProcessor: MetadataProcessor,
        metadataHandler: MetadataHandler<KeyValueHandler>,
        buffer: StringBuilder
    ): StringBuilder {
        val kvf = KeyValueFormatter("[CONTEXT ", " ]", buffer)
        metadataProcessor.process(metadataHandler, kvf)
        kvf.done()
        return buffer
    }

    /**
     * Returns the single literal value as a string.
     *
     * This method must never be called if the log data has arguments to be formatted.
     *
     * This method is designed to be paired with
     * [mustBeFormatted] and can always be safely called if that
     * method returned `false` for the same log data.
     *
     * @param logData The log statement data.
     *
     * @return the single logged value as a string.
     * @throws IllegalStateException
     *         if the log data had arguments to be formatted
     *         (i.e., there was a template context).
     */
    public fun getLiteralLogMessage(logData: LogData): String =
        logData.literalArgument.safeToString()

    /**
     * An internal helper method for logger backends which are aggressively
     * optimized for performance.
     *
     * This method is a best-effort optimization and should not be necessary for most
     * implementations. It is not a stable API and may be removed at some point in the future.
     *
     * This method attempts to determine, for the given log data and log metadata, if the
     * default message formatting performed by the other methods in this class would just
     * result in the literal log message being used, with no additional formatting.
     *
     * If this method returns `false` then the literal log message can be obtained via
     * [getLiteralLogMessage], otherwise it must be formatted manually.
     *
     * By calling this class it is possible to more easily detect cases where using buffers to
     * format the log message is not required. Obviously, a logger backend may have its own reasons
     * for needing buffering (e.g., prepending log site data), and those must also be taken
     * into account.
     *
     * @param logData The log statement data.
     * @param metadata The metadata intended to be formatted with the log statement.
     * @param keysToIgnore A set of metadata keys which are known not to appear in
     *        the final formatted message.
     */
    public fun mustBeFormatted(
        logData: LogData,
        metadata: MetadataProcessor,
        keysToIgnore: Set<MetadataKey<*>>
    ): Boolean {
        // If there are logged arguments or more metadata keys than can be ignored,
        // we fail immediately, which avoids the cost of creating the metadata key set
        // (so don't remove the size check).
        return logData.templateContext != null ||
                metadata.keyCount() > keysToIgnore.size ||
                !keysToIgnore.containsAll(metadata.keySet())
    }

    /**
     * Returns a new "simple" formatter which ignores the given set of metadata keys.
     *
     * The caller must ensure that the given set is effectively immutable.
     */
    private fun newFormatter(keysToIgnore: Set<MetadataKey<*>>): LogMessageFormatter {
        return object : LogMessageFormatter() {
            private val handler: MetadataHandler<KeyValueHandler> =
                MetadataKeyValueHandlers.getDefaultHandler(keysToIgnore)

            override fun append(
                logData: LogData,
                metadata: MetadataProcessor,
                buffer: StringBuilder
            ): StringBuilder {
                BaseMessageFormatter.appendFormattedMessage(logData, buffer)
                return appendContext(metadata, handler, buffer)
            }

            override fun format(logData: LogData, metadata: MetadataProcessor): String {
                return if (mustBeFormatted(logData, metadata, keysToIgnore)) {
                    append(logData, metadata, StringBuilder()).toString()
                } else {
                    getLiteralLogMessage(logData)
                }
            }
        }
    }
}
