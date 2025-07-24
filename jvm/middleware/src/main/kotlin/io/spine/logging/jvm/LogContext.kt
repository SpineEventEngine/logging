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

package io.spine.logging.jvm

import io.spine.annotation.VisibleForTesting
import io.spine.logging.jvm.backend.LogData
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.backend.Platform
import io.spine.logging.jvm.backend.TemplateContext
import io.spine.logging.jvm.context.Tags
import io.spine.logging.jvm.parser.MessageParser
import io.spine.logging.jvm.JvmLogSite.Companion.injectedLogSite
import io.spine.logging.jvm.util.Checks.checkNotNull
import io.spine.reflect.CallerFinder.stackForCallerOf
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * The base context for a logging statement, which implements the base logging API.
 *
 * This class is an implementation of the base [MiddlemanApi] interface and acts as a holder
 * for any state applied to the log statement during the fluent call sequence. The lifecycle of a
 * logging context is very short; it is created by a logger, usually in response to a call to the
 * [AbstractLogger.at] method, and normally lasts only as long as the log statement.
 *
 * This class should not be visible to normal users of the logging API and it is only needed when
 * extending the API to add more functionality. In order to extend the logging API and add methods
 * to the fluent call chain, the `LoggingApi` interface should be extended to add any new
 * methods, and this class should be extended to implement them. A new logger class will then be
 * needed to return the extended context.
 *
 * Logging contexts are not thread-safe.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogContext.java)
 *      for historical context.
 */
public abstract class LogContext<LOGGER : AbstractLogger<API>, API : MiddlemanApi<API>> :
    MiddlemanApi<API>, LogData {

    public companion object {
        /**
         * The text logged when both message and parameters are `null`.
         */
        private const val NULL_MESSAGE = "<null>"

        /**
         * A simple token used to identify cases where a single literal value is logged. Note that this
         * instance must be unique, and it is important not to replace this with `""` or any other
         * value than might be interned and be accessible to code outside this class.
         */
        @Suppress("StringOperationCanBeSimplified")
        private val LITERAL_VALUE_MESSAGE = String()
    }

    /**
     * The predefined metadata keys used by the default logging API. Backend implementations can use
     * these to identify metadata added by the core logging API.
     */
    // TODO: Reevaluate this whole strategy before open-sourcing.
    public class Key private constructor() {

        public companion object {
            /**
             * The key associated with a [Throwable] cause to be associated with the log message.
             * This value is set by [MiddlemanApi.withCause].
             */
            @JvmField
            public val LOG_CAUSE: MetadataKey<Throwable> =
                MetadataKey.single("cause", Throwable::class.java)

            /**
             * The key associated with a rate limiting counter for "1-in-N" rate limiting. The value is
             * set by [MiddlemanApi.every].
             */
            @JvmField
            public val LOG_EVERY_N: MetadataKey<Int> =
                MetadataKey.single("ratelimit_count", Int::class.javaObjectType)

            /**
             * The key associated with a rate limiting counter for "1-in-N" randomly sampled rate
             * limiting. The value is set by [MiddlemanApi.onAverageEvery].
             */
            @JvmField
            public val LOG_SAMPLE_EVERY_N: MetadataKey<Int> =
                MetadataKey.single("sampling_count", Int::class.javaObjectType)

            /**
             * The key associated with a rate-limiting period for "at most once every N" rate limiting.
             * The value is set by [MiddlemanApi.atMostEvery].
             */
            @JvmField
            internal val LOG_AT_MOST_EVERY: MetadataKey<RateLimitPeriod> =
                MetadataKey.single("ratelimit_period", RateLimitPeriod::class.java)

            /**
             * The key associated with a count of rate limited logs. This is only public so backends can
             * reference the key to control formatting.
             */
            @JvmField
            public val SKIPPED_LOG_COUNT: MetadataKey<Int> =
                MetadataKey.single("skipped", Int::class.javaObjectType)

            /**
             * The key associated with a sequence of log site "grouping keys".
             *
             * @see LogSiteGroupingKey
             */
            @JvmField
            public val LOG_SITE_GROUPING_KEY: MetadataKey<Any> = LogSiteGroupingKey()

            /**
             * The key associated with a boolean value indicating whether the log statement was
             * "forced" (i.e. the log level was bypassed when creating the logging context).
             */
            @JvmField
            public val WAS_FORCED: MetadataKey<Boolean> =
                MetadataKey.single("forced", Boolean::class.javaObjectType)

            /**
             * The key associated with a sequence of tags to be associated with the log message.
             * This value is set by [MiddlemanApi.withTags].
             */
            @JvmField
            public val TAGS: MetadataKey<Tags> = object : MetadataKey<Tags>("tags", Tags::class.java, false) {
                override fun emit(tags: Tags, out: KeyValueHandler) {
                    for (entry in tags.asMap().entries) {
                        val values = entry.value
                        if (values.isNotEmpty()) {
                            for (value in values) {
                                out.handle(entry.key, value)
                            }
                        } else {
                            out.handle(entry.key, null)
                        }
                    }
                }
            }

            /**
             * Key associated with the metadata for specifying additional stack information with a log
             * statement.
             */
            private val CONTEXT_STACK_SIZE: MetadataKey<StackSize> =
                MetadataKey.single("stack_size", StackSize::class.java)
        }
    }

    // TODO: Aggressively attempt to reduce the number of fields in this instance.

    /** Additional metadata for this log statement (added via fluent API methods). */
    private var mutableMetadata: MutableMetadata? = null

    /** The log site information for this log statement (set immediately prior to post-processing). */
    private var logSiteValue: JvmLogSite? = null

    /** Rate limit status (only set if rate limiting occurs). */
    private var rateLimitStatus: RateLimitStatus? = null

    /** The template context if formatting is required (set only after post-processing). */
    private var templateContextValue: TemplateContext? = null

    /** The log arguments (set only after post-processing). */
    private var args: Array<Any?>? = null

    // ---- LogData API Properties ----

    public final override val level: Level
    public final override val timestampNanos: Long
    public final override val loggerName: String?
        get() = getLogger().getBackend().loggerName
    public final override val logSite: JvmLogSite
        get() = logSiteValue ?: throw IllegalStateException(
            "cannot request log site information prior to postProcess()"
        )
    public final override val templateContext: TemplateContext?
        get() = templateContextValue
    public final override val arguments: Array<Any?>
        get() {
            if (templateContextValue == null) {
                throw IllegalStateException(
                    "cannot get arguments unless a template context exists"
                )
            }
            return args!!
        }
    public final override val literalArgument: Any?
        get() {
            if (templateContextValue != null) {
                throw IllegalStateException(
                    "Cannot get literal argument if a template context exists: $templateContextValue"
                )
            }
            return args!![0]
        }
    public final override val metadata: Metadata
        get() = mutableMetadata ?: Metadata.empty()

    /**
     * Creates a logging context with the specified level, and with a timestamp obtained from the
     * configured logging [Platform].
     *
     * @param level
     *         the log level for this log statement.
     * @param isForced
     *         whether to force this log statement (see [wasForced] for details).
     */
    protected constructor(level: Level, isForced: Boolean) : this(level, isForced, Platform.getCurrentTimeNanos())

    /**
     * Creates a logging context with the specified level and timestamp. This constructor is
     * provided only for testing when timestamps need to be injected. In general, subclasses would only need
     * to call this constructor when testing additional API methods which require timestamps (e.g.
     * additional rate limiting functionality). Most unit tests for logger subclasses should not
     * test the value of the timestamp at all, since this is already well tested elsewhere.
     *
     * @param level
     *         the log level for this log statement.
     * @param isForced
     *         whether to force this log statement (see [wasForced] for details).
     * @param timestampNanos
     *         the nanosecond timestamp for this log statement.
     */
    protected constructor(level: Level, isForced: Boolean, timestampNanos: Long) {
        this.level = checkNotNull(level, "level")
        this.timestampNanos = timestampNanos
        if (isForced) {
            addMetadata(Key.WAS_FORCED, true)
        }
    }

    /**
     * Returns the current API (which is just the concrete sub-type of this instance). This is
     * returned by fluent methods to continue the fluent call chain.
     */
    protected abstract fun api(): API

    // ---- Logging Context Constants ----

    /**
     * Returns the logger which created this context. This is implemented as an abstract method to
     * save a field in every context.
     */
    protected abstract fun getLogger(): LOGGER

    /**
     * Returns the constant no-op logging API, which can be returned by fluent methods in extended
     * logging contexts to efficiently disable logging. This is implemented as an abstract method to
     * save a field in every context.
     */
    protected abstract fun noOp(): API

    /** Returns the message parser used for all log statements made through this logger. */
    protected abstract fun getMessageParser(): MessageParser

    public final override fun wasForced(): Boolean {
        // Check explicit TRUE here because findValue() can return null (which would fail unboxing).
        return mutableMetadata != null && mutableMetadata!!.findValue(Key.WAS_FORCED) == true
    }

    // ---- Mutable Metadata ----

    /**
     * Adds the given key/value pair to this logging context. If the key cannot be repeated, and
     * there is already a value for the key in the metadata, then the existing value is replaced,
     * otherwise the value is added at the end of the metadata.
     *
     * @param key
     *         the metadata key (see [LogData]).
     * @param value
     *         the metadata value.
     */
    public fun <T : Any> addMetadata(key: MetadataKey<T>, value: T) {
        if (mutableMetadata == null) {
            mutableMetadata = MutableMetadata()
        }
        mutableMetadata!!.addValue(key, value)
    }

    /**
     * Removes all values for the given key from this logging context.
     *
     * @param key
     *         the metadata key (see [LogData]).
     */
    public fun removeMetadata(key: MetadataKey<*>) {
        mutableMetadata?.removeAllValues(key)
    }

    // TODO: Continue with the rest of the methods...
}
