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

import io.spine.annotation.VisibleForTesting
import io.spine.logging.jvm.JvmLogSite.Companion.injectedLogSite
import io.spine.logging.jvm.backend.LogData
import io.spine.logging.jvm.backend.Metadata
import io.spine.logging.jvm.backend.Platform
import io.spine.logging.jvm.backend.TemplateContext
import io.spine.logging.jvm.context.Tags
import io.spine.logging.jvm.parser.MessageParser
import io.spine.logging.jvm.util.Checks.checkNotNull
import io.spine.reflect.CallerFinder.stackForCallerOf
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
 * @property level The log level of the log statement that this context was created for.
 * @param isForced Whether to force this log statement (see [wasForced] for details).
 * @property timestampNanos The timestamp of the log statement that this context is associated with.
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogContext.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@Suppress(
    "LargeClass",
    "TooManyFunctions",
)
public abstract class LogContext<LOGGER : AbstractLogger<API>, API : MiddlemanApi<API>>
protected constructor(
    final override val level: Level,
    isForced: Boolean,
    final override val timestampNanos: Long
) : MiddlemanApi<API>, LogData {

    /**
     * Additional metadata for this log statement (added via fluent API methods).
     */
    private var _metadata: MutableMetadata? = null

    /**
     * The log site information for this log statement (set immediately prior to post-processing).
     */
    private var logSiteInfo: JvmLogSite? = null

    /**
     * Rate limit status (only set if rate limiting occurs).
     */
    private var rateLimitStatus: RateLimitStatus? = null

    /**
     * The template context if formatting is required (set only after post-processing).
     */
    private var logTemplateContext: TemplateContext? = null

    /**
     * The log arguments (set only after post-processing).
     */
    private var args: Array<Any?>? = null

    /**
     * Creates a logging context with the specified level, and with a timestamp obtained from the
     * configured logging [Platform].
     *
     * @param level The log level for this log statement.
     * @param isForced Whether to force this log statement (see [wasForced] for details).
     */
    protected constructor(level: Level, isForced: Boolean) :
            this(level, isForced, Platform.getCurrentTimeNanos())

    init {
        if (isForced) {
            addMetadata(Key.WAS_FORCED, true)
        }
    }

    /**
     * Returns the current API (which is just the concrete sub-type of this instance).
     *
     * This is returned by fluent methods to continue the fluent call chain.
     */
    protected abstract fun api(): API

    /**
     * Returns the logger which created this context.
     *
     * This is implemented as an abstract method to save a field in every context.
     */
    protected abstract fun getLogger(): LOGGER

    /**
     * Returns the constant no-op logging API, which can be returned by fluent methods in extended
     * logging contexts to efficiently disable logging.
     *
     * This is implemented as an abstract method to save a field in every context.
     */
    protected abstract fun noOp(): API

    /**
     * Returns the message parser used for all log statements made through this logger.
     */
    protected abstract fun getMessageParser(): MessageParser

    public final override val loggerName: String?
        get() = getLogger().getName()

    public final override val logSite: JvmLogSite
        get() = logSiteInfo ?: error(
            "Cannot request log site information prior to `postProcess()`."
        )

    public final override val templateContext: TemplateContext?
        get() = logTemplateContext

    public final override val arguments: Array<Any?>
        get() {
            if (logTemplateContext == null) {
                error("Cannot get arguments unless a template context exists.")
            }
            return args!!
        }

    public final override val literalArgument: Any?
        get() {
            if (logTemplateContext != null) {
                error(
                    "Cannot get literal argument if a template context exists: $logTemplateContext."
                )
            }
            return args!![0]
        }

    public final override fun wasForced(): Boolean {
        // Check explicit TRUE here because findValue() can return null (which would fail unboxing).
        return _metadata != null && true == _metadata!!.findValue(Key.WAS_FORCED)
    }

    /**
     * Returns any additional metadata for this log statement.
     *
     * When called outside of the logging backend, this method may return different values at
     * different times (i.e., it may initially return a shared static "empty" metadata object and
     * later return a different implementation).
     * As such, it is not safe to cache the instance returned by this method or to attempt to
     * cast it to any particular implementation.
     */
    public final override val metadata: Metadata
        get() = _metadata ?: Metadata.empty()

    /**
     * Adds the given key/value pair to this logging context. If the key cannot be repeated, and
     * there is already a value for the key in the metadata, then the existing value is replaced,
     * otherwise the value is added at the end of the metadata.
     *
     * @param key the metadata key (see [LogData]).
     * @param value the metadata value.
     */
    protected fun <T : Any> addMetadata(key: MetadataKey<T>, value: T) {
        if (_metadata == null) {
            _metadata = MutableMetadata()
        }
        _metadata!!.addValue(key, value)
    }

    /**
     * Removes all key/value pairs with the specified key. Note that this method does not resize
     * any underlying backing arrays or other storage as logging contexts are expected to be short
     * lived.
     *
     * @param key the metadata key (see [LogData]).
     */
    protected fun removeMetadata(key: MetadataKey<*>) {
        _metadata?.removeAllValues(key)
    }

    /**
     * A callback which can be overridden to implement post processing of logging contexts prior to
     * passing them to the backend.
     *
     * ## Basic Responsibilities
     *
     * This method is responsible for:
     *
     * 1. Performing any rate-limiting operations specific to the extended API.
     * 2. Updating per log-site information (e.g. for debug metrics).
     * 3. Adding any additional metadata to this context.
     * 4. Returning whether logging should be attempted.
     *
     * Implementations of this method must always call `super.postProcess()` first with the
     * given log site key:
     *
     * ```kotlin
     * protected fun postProcess(logSiteKey: LogSiteKey?): Boolean {
     *     val shouldLog = super.postProcess(logSiteKey)
     *     // Handle rate limiting if present.
     *     // Add additional metadata etc.
     *     return shouldLog
     * }
     * ```
     *
     * ## Log Site Keys
     *
     * If per log-site information is needed during post-processing, it should be stored using a
     * [LogSiteMap]. This will correctly handle "specialized" log-site keys and remove the risk
     * of memory leaks due to retaining unused log site data indefinitely.
     *
     * Note that the given `logSiteKey` can be more specific than the [JvmLogSite]
     * of a log statement (i.e. a single log statement can have multiple distinct versions of
     * its state). See [per] for more information.
     *
     * If a log statement cannot be identified uniquely, then `logSiteKey` will be `null`, and
     * this method must behave exactly as if the corresponding fluent method had not been
     * invoked. On a system in which log site information is *unavailable*:
     *
     * ```kotlin
     * logger.atInfo().every(100).withCause(e).log("Some message")
     * ```
     *
     * should behave exactly the same as:
     *
     * ```kotlin
     * logger.atInfo().withCause(e).log("Some message")
     * ```
     *
     * ## Rate Limiting and Skipped Logs
     *
     * When handling rate limiting, [updateRateLimiterStatus] should be
     * called for each active rate limiter. This ensures that even if logging does not occur, the
     * number of "skipped" log statements is recorded correctly and emitted for the next allowed log.
     *
     * If `postProcess()` returns `false` without updating the rate limit status, the
     * log statement may not be counted as skipped. In some situations this is desired,
     * but either way the extended logging API should make it clear to the user
     * (via documentation) what will happen.
     * However, in most cases `postProcess()` is only expected to return `false` due to
     * rate limiting.
     *
     * If rate limiters are used there are still situations in which `postProcess()` can
     * return `true`, but logging will not occur. This is due to race conditions around the
     * resetting of rate limiter state. A `postProcess()` method can "early exit" as soon as
     * `shouldLog` is false, but should assume logging will occur while it remains `true`.
     *
     * If a method in the logging chain determines that logging should definitely not occur, it may
     * choose to return the `NoOp` logging API at that point. However this will bypass any
     * post-processing, and no rate limiter state will be updated. This is sometimes desirable, but
     * the API documentation should make it clear to the user as to which behaviour occurs.
     *
     * For example, level selector methods (such as `atInfo()`) return the `NoOp` API
     * for "disabled" log statements, and these have no effect on rate limiter state, and will not
     * update the "skipped" count. This is fine because controlling logging via log level
     * selection is not conceptually a form of "rate limiting".
     *
     * The default implementation of this method enforces the rate limits as set
     * by [every] and [atMostEvery].
     *
     * @param logSiteKey used to lookup persistent, per log statement, state.
     * @return true if logging should be attempted (usually based on rate limiter state).
     */
    protected open fun postProcess(logSiteKey: LogSiteKey?): Boolean {
        // Without metadata there's nothing to post-process.
        if (_metadata != null) {
            // Without a log site we ignore any log-site specific behaviour.
            if (logSiteKey != null) {
                // Since the base class postProcess() should be invoked before subclass logic,
                // we can set the initial status here. Subclasses can combine this with other
                // rate limiter statuses by calling `updateRateLimiterStatus()` before we get
                // back into shouldLog().
                var status = DurationRateLimiter.check(_metadata!!, logSiteKey, timestampNanos)
                status = RateLimitStatus.combine(
                    status,
                    CountingRateLimiter.check(_metadata!!, logSiteKey)
                )
                status = RateLimitStatus.combine(
                    status,
                    SamplingRateLimiter.check(_metadata!!, logSiteKey)
                )
                this.rateLimitStatus = status

                // Early exit as soon as we know the log statement is disallowed.
                // A subclass may still do post processing but should never re-enable the log.
                if (status == RateLimitStatus.DISALLOW) {
                    return false
                }
            }

            // This does not affect whether logging will occur, only what
            // additional data it contains.
            val stackSize = _metadata!!.findValue(Key.CONTEXT_STACK_SIZE)
            if (stackSize != null) {
                // We add this information to the stack trace exception,
                // so it doesn't need to go here.
                removeMetadata(Key.CONTEXT_STACK_SIZE)
                // IMPORTANT: Skipping at least 1 stack frame below is essential for correctness,
                // since postProcess() can be overridden, so the stack could look like:
                //
                // ^  UserCode::someMethod       << we want to start here and skip everything below
                // |  LogContext::log
                // |  LogContext::shouldLog
                // |  OtherChildContext::postProcess
                // |  ChildContext::postProcess  << this is *not* the caller of `LogContext` we're after
                // \- LogContext::postProcess    << we are here
                //
                // By skipping the initial code inside this method, we don't trigger any stack
                // capture until after the "log" method.
                val context = LogSiteStackTrace(
                    _metadata!!.findValue(Key.LOG_CAUSE),
                    stackSize,
                    stackForCallerOf(LogContext::class.java, stackSize.maxDepth, 1)
                )
                // The "cause" is a unique metadata key, we must replace any existing value.
                addMetadata(Key.LOG_CAUSE, context)
            }
        }
        // By default, no restrictions apply, so we should log.
        return true
    }

    /**
     * Callback to allow custom log contexts to apply additional rate limiting behaviour.
     * This should be called from within an overriden `postProcess()` method.
     * Typically, this is invoked after calling `super.postProcess(logSiteKey)`, such as:
     *
     * ```kotlin
     * protected fun postProcess(logSiteKey: LogSiteKey?): Boolean {
     *     val shouldLog = super.postProcess(logSiteKey)
     *     // Even if `shouldLog` is false, we still call the rate limiter to update its state.
     *     shouldLog = shouldLog && updateRateLimiterStatus(CustomRateLimiter.check(...))
     *     if (shouldLog) {
     *         // Maybe add additional metadata here...
     *     }
     *     return shouldLog
     * }
     * ```
     *
     * See [RateLimitStatus] for more information on how to implement custom
     * rate limiting.
     *
     * @param status A rate-limiting status, or `null` if the rate limiter was not active.
     * @return whether logging will occur based on the current combined state of
     *         active rate limiters.
     */
    protected fun updateRateLimiterStatus(status: RateLimitStatus?): Boolean {
        rateLimitStatus = RateLimitStatus.combine(rateLimitStatus, status)
        return rateLimitStatus != RateLimitStatus.DISALLOW
    }

    /**
     * Pre-processes log metadata and determines whether we should make the pending logging call.
     *
     * Note that this call is made inside each of the individual log methods (rather than in
     * `logImpl()`) because it is better to decide whether we are actually going to do the
     * logging before we pay the price of creating a varargs array and doing things like
     * auto-boxing of arguments.
     */
    private fun shouldLog(): Boolean {
        // The log site may have already been injected via "withInjectedLogSite()" or similar.
        if (logSiteInfo == null) {
            // From the point at which we call inferLogSite() we can skip 1 additional method
            // (the `shouldLog()` method itself) when looking up the stack to find the log() method.
            logSiteInfo = checkNotNull(
                Platform.getCallerFinder().findLogSite(LogContext::class.java, 1),
                "A logger backend must not return a null `LogSite`."
            )
        }
        var logSiteKey: LogSiteKey? = null
        if (logSiteInfo != JvmLogSite.invalid) {
            logSiteKey = logSiteInfo
            // Log site keys are only modified when we have metadata in the log statement.
            if (_metadata != null && _metadata!!.size() > 0) {
                logSiteKey = specializeLogSiteKeyFromMetadata(logSiteKey!!, _metadata!!)
            }
        }
        val shouldLog = postProcess(logSiteKey)
        if (rateLimitStatus != null && logSiteKey != null) {
            // We check rate limit status even if it is "DISALLOW" to update the skipped logs count.
            val skippedLogs = RateLimitStatus.checkStatus(
                rateLimitStatus!!,
                logSiteKey,
                _metadata ?: Metadata.empty()
            )
            if (shouldLog && skippedLogs > 0) {
                if (_metadata != null) {
                    _metadata!!.addValue(Key.SKIPPED_LOG_COUNT, skippedLogs)
                }
            }
            // checkStatus() returns -1 in two cases:
            // 1. We passed it the DISALLOW status.
            // 2. We passed it an "allow" status, but multiple threads were racing to try and
            //    reset the rate limiters, and this thread lost.
            // Either way we should suppress logging.
            return shouldLog && (skippedLogs >= 0)
        }
        return shouldLog
    }

    /**
     * Make the backend logging call. This is the point at which we have paid the price of creating
     * a varargs array and doing any necessary auto-boxing.
     */
    private fun logImpl(message: String?, vararg args: Any?) {
        this.args = arrayOf(*args)
        // Evaluate any (rare) LazyArg instances early.
        // This may throw exceptions from user code, but it seems reasonable
        // to propagate them in this case.
        // They would have been thrown if the argument was evaluated at the call site anyway.
        for (n in this.args!!.indices) {
            if (this.args!![n] is LazyArg<*>) {
                this.args!![n] = (this.args!![n] as LazyArg<*>).evaluate()
            }
        }
        // Using "!=" is fast and sufficient here because the only real case this should
        // be skipping is when we called `log(String)` or `log()`, which should not result in
        // a template being created.
        // DO NOT replace this with a string instance which can be interned, or use equals() here,
        // since that could mistakenly treat other calls to log(String, Object...) incorrectly.
        if (message !== LITERAL_VALUE_MESSAGE) {
            val msg = message ?: NULL_MESSAGE
            this.logTemplateContext = TemplateContext(getMessageParser(), msg)
        }
        // Right at the end of processing add any tags injected by the platform.
        // Any tags supplied at the log site are merged with the injected tags
        // (though this should be very rare).
        val tags = Platform.getInjectedTags()
        if (!tags.isEmpty()) {
            val logSiteTags = _metadata?.findValue(Key.TAGS)
            val finalTags = if (logSiteTags != null) {
                tags.merge(logSiteTags)
            } else {
                tags
            }
            addMetadata(Key.TAGS, finalTags)
        }
        // Pass the completed log data to the backend (it should not be modified after this point).
        getLogger().write(this)
    }

    // ---- Log site injection (used by pre-processors and special cases) ----

    public final override fun withInjectedLogSite(logSite: JvmLogSite?): API {
        // First call wins (since auto-injection will typically target the `log()` method at
        // the end of the chain and might not check for previous explicit injection).
        // In particular it MUST be allowed for a caller to specify the "INVALID" log site, and
        // have that set the field here to disable log site lookup at this log statement
        // (though passing "null" is a no-op).
        if (this.logSiteInfo == null && logSite != null) {
            this.logSiteInfo = logSite
        }
        return api()
    }

    @LogSiteInjector
    public final override fun withInjectedLogSite(
        internalClassName: String,
        methodName: String,
        encodedLineNumber: Int,
        sourceFileName: String?
    ): API {
        val logSite = injectedLogSite(
            internalClassName, methodName, encodedLineNumber, sourceFileName
        )
        return withInjectedLogSite(logSite)
    }

    public final override fun isEnabled(): Boolean {
        // We can't guarantee that all logger implementations will return instances of this class
        // _only_ when logging is enabled, so if would be potentially unsafe to just return
        // `true` here.
        // It's not worth caching this result in the instance because calls to this
        // method should be rare and they are only going to be made once per instance anyway.
        return wasForced() || getLogger().doIsLoggable(level)
    }

    public final override fun <T : Any> with(key: MetadataKey<T>, value: T?): API {
        if (value != null) {
            @Suppress("UNCHECKED_CAST")
            addMetadata(key as MetadataKey<Any>, value as Any)
        }
        return api()
    }

    public final override fun with(key: MetadataKey<Boolean>): API = with(key, true)

    public override fun <T> per(key: T?, strategy: LogPerBucketingStrategy<in T>): API {
        // Skip calling the bucketer for null so implementations don't need to check.
        return if (key != null) {
            val bucketedKey = strategy.doApply(key)
            with(Key.LOG_SITE_GROUPING_KEY, bucketedKey)
        } else {
            api()
        }
    }

    public final override fun per(key: Enum<*>?): API {
        return if (key != null) with(Key.LOG_SITE_GROUPING_KEY, key) else api()
    }

    public override fun per(scopeProvider: LoggingScopeProvider): API =
        with(Key.LOG_SITE_GROUPING_KEY, scopeProvider.getCurrentScope())

    public final override fun withCause(cause: Throwable?): API {
        return if (cause != null) with(Key.LOG_CAUSE, cause) else api()
    }

    public override fun withStackTrace(size: StackSize): API {
        // Unlike other metadata methods, the "no-op" value is not null.
        if (size != StackSize.NONE) {
            addMetadata(Key.CONTEXT_STACK_SIZE, size)
        }
        return api()
    }

    public final override fun every(n: Int): API = everyImpl(Key.LOG_EVERY_N, n, "rate limit")

    public final override fun onAverageEvery(n: Int): API =
        everyImpl(Key.LOG_SAMPLE_EVERY_N, n, "sampling")

    private fun everyImpl(key: MetadataKey<Int>, n: Int, label: String): API {
        // See wasForced() for discussion as to why this occurs before argument checking.
        if (wasForced()) {
            return api()
        }
        require(n > 0) {
            "`$label` count must be positive, but was: $n."
        }
        // 1-in-1 rate limiting is a no-op.
        if (n > 1) {
            addMetadata(key, n)
        }
        return api()
    }

    public final override fun atMostEvery(n: Int, unit: TimeUnit): API {
        // See wasForced() for discussion as to why this occurs before argument checking.
        if (wasForced()) {
            return api()
        }
        require(n >= 0) { "Rate limit period cannot be negative: $n." }

        // Rate limiting with a zero length period is a no-op, but if the time unit is
        // nanoseconds then the value is rounded up inside the rate limit object.
        if (n > 0) {
            addMetadata(Key.LOG_AT_MOST_EVERY, DurationRateLimiter.newRateLimitPeriod(n, unit))
        }
        return api()
    }

    /*
     * Note that while all log statements look almost identical to each other, it is vital that we
     * keep the 'shouldLog()' call outside of the call to 'logImpl()' so we can decide whether or not
     * to abort logging before we do any varargs creation.
     */

    public final override fun log() {
        if (shouldLog()) {
            logImpl(LITERAL_VALUE_MESSAGE, "")
        }
    }

    public final override fun log(msg: () -> String?) {
        if (shouldLog()) {
            logImpl(LITERAL_VALUE_MESSAGE, msg())
        }
    }

    @Suppress("SpreadOperator")
    public final override fun logVarargs(message: String, params: Array<Any?>?) {
        if (shouldLog()) {
            // Copy the varargs array (because we didn't create it and this is quite a rare case).
            logImpl(message, *(params?.copyOf(params.size) ?: emptyArray()))
        }
    }

    /**
     * The predefined metadata keys used by the default logging API.
     *
     * Backend implementations can use these to identify metadata added by the core logging API.
     */
    public object Key {

        /**
         * The key associated with a [Throwable] cause to be associated with the log message.
         *
         * This value is set by [MiddlemanApi.withCause].
         */
        @JvmField
        public val LOG_CAUSE: MetadataKey<Throwable> =
            MetadataKey.single("cause", Throwable::class.java)

        /**
         * The key associated with a rate limiting counter for "1-in-N" rate limiting.
         *
         * The value is set by [MiddlemanApi.every].
         */
        @JvmField
        public val LOG_EVERY_N: MetadataKey<Int> =
            MetadataKey.single("ratelimit_count", Int::class.javaObjectType)

        /**
         * The key associated with a rate limiting counter for "1-in-N" randomly sampled rate
         * limiting.
         *
         * The value is set by [MiddlemanApi.onAverageEvery].
         */
        @JvmField
        public val LOG_SAMPLE_EVERY_N: MetadataKey<Int> =
            MetadataKey.single("sampling_count", Int::class.javaObjectType)

        /**
         * The key associated with a rate-limiting period for "at most once every N" rate limiting.
         *
         * The value is set by [MiddlemanApi.atMostEvery].
         */
        @JvmField
        public val LOG_AT_MOST_EVERY: MetadataKey<RateLimitPeriod> =
            MetadataKey.single("ratelimit_period", RateLimitPeriod::class.java)

        /**
         * The key associated with a count of rate limited logs.
         *
         * This is only public so backends can reference the key to control formatting.
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
         * The key associated with a `Boolean` value used to specify that the log statement
         * must be emitted.
         *
         * Forcing a log statement ensures that the `LoggerBackend` is passed the
         * `LogData` for this log statement regardless of the backend's log level or any other
         * filtering or rate limiting, which might normally occur.
         *
         * If a log statement is forced, this key will be set immediately on creation of the
         * logging context and will be visible to both fluent methods and post-processing.
         *
         * Filtering and rate-limiting methods must check for this value and should treat forced
         * log statements as not having had any filtering or rate limiting applied. For example, if
         * the following log statement was forced:
         *
         * ```kotlin
         * logger.atInfo().withCause(e).atMostEvery(1, MINUTES).log("Message...")
         * ```
         *
         * it should behave as if the rate-limiting methods were never called, such as:
         *
         * ```kotlin
         * logger.atInfo().withCause(e).log("Message...")
         * ```
         *
         * As well as no longer including any rate-limiting metadata for the forced log statement,
         * this also has the effect of never interfering with the rate-limiting of this log
         * statement for other callers.
         *
         * The decision of whether to force a log statement is expected to be made based upon
         * debug values provided by the logger which come from a scope greater than the log
         * statement itself. Thus it makes no sense to provide a public method to set this value
         * programmatically for a log statement.
         */
        @JvmField
        public val WAS_FORCED: MetadataKey<Boolean> =
            MetadataKey.single("forced", Boolean::class.javaObjectType)

        /**
         * The key associated with any injected [Tags].
         *
         * If tags are injected, they are added after post-processing if the log site is enabled.
         * Thus they are not available to the `postProcess()` method itself. The rationale is
         * that a log statement's behavior should only be affected by code at the log site (other
         * than "forcing" log statements, which is slightly a special case).
         *
         * Tags can be added at the log site, although this should rarely be necessary and using
         * normal log message arguments is always the preferred way to indicate unstructured log
         * data. Users should never build new [Tags] instances just to pass them into a log
         * statement.
         */
        @JvmField
        public val TAGS: MetadataKey<Tags> = object : MetadataKey<Tags>(
            "tags",
            Tags::class.java,
            false
        ) {
            override fun emit(value: Tags, kvh: KeyValueHandler) {
                for ((key, values) in value.asMap()) {
                    if (values.isNotEmpty()) {
                        for (v in values) {
                            kvh.handle(key, v)
                        }
                    } else {
                        kvh.handle(key, null)
                    }
                }
            }
        }

        /**
         * Key associated with the metadata for specifying additional stack information with a log
         * statement.
         */
        internal val CONTEXT_STACK_SIZE: MetadataKey<StackSize> =
            MetadataKey.single("stack_size", StackSize::class.java)
    }

    public companion object {
        /**
         * The text logged when both message and parameters are `null`.
         */
        private const val NULL_MESSAGE = "<null>"

        /**
         * A simple token used to identify cases where a single literal value is logged.
         *
         * Note that this instance must be unique, and it is important not to replace this
         * with `""` or any other value than might be interned and be accessible to code
         * outside this class.
         */
        @Suppress("StringOperationCanBeSimplified")
        private val LITERAL_VALUE_MESSAGE = String()

        // WARNING: If we ever start to use combined log-site and scoped context metadata here via
        // MetadataProcessor, there's an issue. It's possible that the same scope can appear in both
        // the context and the log-site, and multiplicity should not matter; BUT IT DOES! This means
        // that a log statement executed both in and outside of the context would currently see
        // different keys, when they should be the same. To fix this, specialization must be changed
        // to ignore repeated scopes. For now we only see log site metadata so this is not an issue.
        //
        // TODO: Ignore repeated scopes (e.g. use a Bloom Filter mask on each scope).
        // TODO: Make a proper iterator on Metadata or use MetadataProcessor.
        //
        @VisibleForTesting
        internal fun specializeLogSiteKeyFromMetadata(
            logSiteKey: LogSiteKey,
            metadata: Metadata
        ): LogSiteKey {
            var result = logSiteKey
            for (n in 0 until metadata.size()) {
                if (Key.LOG_SITE_GROUPING_KEY == metadata.getKey(n)) {
                    val groupByQualifier = metadata.getValue(n)
                    // Logging scopes need special treatment to handle tidying up when closed.
                    result = if (groupByQualifier is LoggingScope) {
                        groupByQualifier.doSpecialize(result)
                    } else {
                        SpecializedLogSiteKey.of(result, groupByQualifier)
                    }
                }
            }
            return result
        }
    }
}
