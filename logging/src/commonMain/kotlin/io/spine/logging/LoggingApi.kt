/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging

import io.spine.annotation.Internal
import kotlin.time.DurationUnit

/**
 * The basic logging API.
 *
 * An implementation of this API (or an extension of it) will be returned by any logger and
 * forms the basis of the fluent call chain.
 *
 * In typical usage each method in the API, with the exception of the terminal [log]
 * statements, will carry out some simple task (which may involve modifying the context of the log
 * statement) and return the same API for chaining.
 *
 * The exceptions to this are:
 * - Methods which return a NoOp implementation of the API in order to disable logging.
 * - Methods which return an alternate API in order to implement context-specific grammar (though
 *   these alternate APIs should always return the original logging API eventually).
 *
 * A hypothetical example of a context-specific grammar might be:
 * ```kotlin
 * logger.at(WARNING).whenSystem().isLowOnMemory().log("")
 * ```
 * In this example the `whenSystem()` method would return its own API with several
 * context-specific methods (`isLowOnMemory()`, `isThrashing()` etc...).
 * However, each of these sub-APIs must eventually return the original logging API.
 *
 * ### API note
 *
 * It is expected that this class is going to be merged with `io.spine.logging.LoggingApi` of
 * the `logging` module.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LoggingApi.java)
 *   for historical context.
 */
@Suppress("TooManyFunctions")
public interface LoggingApi<API: LoggingApi<API>> {

    /**
     * Associates the given domain with the current log statement.
     */
    public fun withLoggingDomain(domain: LoggingDomain): API

    /**
     * Associates a [Throwable] instance with the current log statement, to be interpreted as
     * the cause of this statement.
     *
     * Typically, this method will be used from within catch blocks to log
     * the caught exception or error. If the cause is `null` then this method has no effect.
     *
     * If this method is called multiple times for a single log statement,
     * the last invocation will take precedence.
     *
     * @param cause The [Throwable] to associate with the log statement, or `null` to remove any.
     */
    public fun withCause(cause: Throwable?): API

    /**
     * Modifies the current log statement to be emitted at most one-in-N times.
     *
     * The specified count must be greater than zero, and it is expected,
     * but not required, that it is constant.
     * In the absence of any other rate limiting, this method always allows
     * the first invocation of any log statement to be emitted.
     *
     * ### Notes
     *
     * If *multiple rate limiters* are used for a single log statement,
     * that log statement will only be emitted once all rate limiters have
     * reached their threshold, and when a log statement is emitted all
     * the rate limiters are reset. In particular for [every] this means
     * that logs need not always be emitted at multiples of `n` if other
     * rate limiters are active, though it will always be at least `n`.
     *
     * When rate limiting is active, a "skipped" count is added to log
     * statements to indicate how many logs were disallowed since
     * the last log statement was emitted.
     *
     * If this method is called multiple times for a single log statement,
     * the last invocation will take precedence.
     *
     * @param n The factor by which to reduce logging frequency.
     * @throws IllegalArgumentException if [n] is not positive.
     */
    public fun every(n: Int): API

    /**
     * Modifies the current log statement to be emitted with likelihood 1 in [n].
     *
     * For example, inserting `onAverageEvery(20)` into a call chain results in
     * approximately 5% as many messages being emitted as before.
     * Unlike the other rate-limiting options, there is no guarantee about
     * when the first such message will be emitted, though it becomes highly
     * likely as the number of calls reaches several times [n].
     *
     * ### Notes
     *
     * If *multiple rate limiters* are used for a single log statement,
     * that log statement will only be emitted once all rate limiters have reached
     * their threshold, and when a log statement is emitted, all the rate limiters
     * are reset. In particular for `onAverageEvery(N)` this means that logs may
     * occur less frequently than one-in-N if other rate limiters are active.
     *
     * When rate limiting is active, a "skipped" count is added to log statements
     * to indicate how many logs were disallowed since the last log statement was emitted.
     *
     * If this method is called multiple times for a single log statement,
     * the last invocation will take precedence.
     *
     * @param n The factor by which to reduce logging frequency; a value of `1` has no effect.
     * @throws IllegalArgumentException if [n] is not positive.
     */
    public fun onAverageEvery(n: Int): API

    /**
     * Modifies the current log statement to be emitted at most once
     * per specified time period.
     *
     * The specified duration must not be negative, and it is expected,
     * but not required, that it is constant.
     * In the absence of any other rate limiting, this method always
     * allows the first invocation of any log statement to be emitted.
     *
     * Note that for performance reasons `atMostEvery()` is explicitly *not* intended to
     * perform "proper" rate limiting to produce a limited average rate over many samples.
     *
     * ### Behaviour
     *
     * A call to `atMostEvery()` will emit the current log statement if:
     * ```
     * currentTimestampNanos >= lastTimestampNanos + unit.toNanos(n)
     * ```
     * where `currentTimestampNanos` is the timestamp of the current log statement and
     * `lastTimestampNanos` is a time stamp of the last log statement that was emitted.
     *
     * The effect of this is that when logging invocation is relatively infrequent,
     * the period between emitted log statements can be higher than the specified duration.
     * For example, if the following log statement were called every 600ms:
     *
     * ```kotlin
     * logger.atFine().atMostEvery(2, SECONDS).log(...)
     * ```
     *
     * logging would occur after `0s`, `2.4s` and `4.8s` (not `4.2s`),
     * giving an effective duration of `2.4s` between log statements over time.
     *
     * Providing a zero-length duration (i.e., `n == 0`) disables rate limiting and
     * makes this method an effective no-op.
     *
     * ### Granularity
     *
     * Because the implementation of this feature relies on a nanosecond timestamp
     * provided by the backend, the actual granularity of the underlying clock used
     * may vary, and it is possible to specify a time period smaller than
     * the smallest visible time increment.
     *
     * If this occurs, then the effective rate limit applied to the log statement
     * will be the smallest available time increment.
     *
     * For example, if the system clock granularity is 1 millisecond, and
     * a log statement is called with `atMostEvery(700, MICROSECONDS)`,
     * the effective rate of logging (even averaged over long periods)
     * could never be more than once every millisecond.
     *
     * ### Notes
     *
     * If *multiple rate limiters* are used for a single log statement, that
     * log statement will only be emitted once all rate limiters have reached their threshold,
     * and when a log statement is emitted all the rate limiters are reset.
     *
     * So even if the rate limit duration has expired, it does not mean that logging will occur.
     *
     * When rate limiting is active, a "skipped" count is added to log statements
     * to indicate how many logs were disallowed since the last log statement was emitted.
     *
     * If this method is called multiple times for a single log statement,
     * the last invocation will take precedence.
     *
     * @param n The minimum number of time units between emitted log statements
     * @param unit The time unit for the duration
     * @throws IllegalArgumentException if [n] is negative.
     */
    public fun atMostEvery(n: Int, unit: DurationUnit): API

    /**
     * Returns true if logging is enabled at the level implied for this API,
     * according to the current logger backend.
     *
     * For example:
     *
     * ```kotlin
     * if (logger.atFine().isEnabled()) {
     *   // Do non-trivial argument processing
     *   logger.atFine().log("Message: %s", value)
     * }
     * ```
     *
     * Note that if logging is enabled for a log level, it does not always follow
     * that the log statement will definitely be written to the backend
     * (due to the effects of other methods in the fluent chain),
     * but if this method returns `false` then it can safely be assumed
     * that no logging will occur.
     *
     * This method is unaffected by additional methods in the fluent chain and
     * should only ever be invoked immediately after the level selector method.
     *
     * In other words, the expression:
     *
     * ```kotlin
     * logger.atFine().every(100).isEnabled()
     * ```
     * is incorrect because it will always behave identically to:
     *
     * ```kotlin
     * logger.atFine().isEnabled()
     * ```
     *
     * ### Implementation Note
     *
     * By avoiding passing a separate `Level` at runtime to determine "loggability", this API
     * makes it easier to coerce bytecode optimizers into doing "dead code" removal on sections
     * guarded by this method.
     *
     * If a proxy logger class is supplied for which:
     * ```
     * logger.atFine()
     * ```
     * unconditionally returns the "NoOp" implementation of the API (in which `isEnabled()`
     * always returns `false`), it becomes simple for bytecode analysis to determine that:
     * ```
     * logger.atFine().isEnabled()
     * ```
     * always evaluates to `false`.
     */
    public fun isEnabled(): Boolean

    /**
     * Terminal log statement when a message is not required.
     *
     * A `log` method must terminate all fluent logging chains, and the no-argument method
     * can be used if there is no need for a log message. For example:
     *
     * ```kotlin
     * logger.at(INFO).withCause(error).log()
     * ```
     *
     * However, as it is good practice to give all log statements a meaningful log message,
     * use of this method should be rare.
     */
    public fun log()

    /**
     * Logs the given string computed by the given function.
     *
     * @param message A lambda that returns the literal string to log. If the lambda returns `null`,
     *       this method will log a string literal reserved by the Logging library for `null`s.
     */
    public fun log(message: () -> String?)

    /**
     * Aggregates stateful logging with respect to a given [key].
     *
     * Normally log statements with conditional behaviour (e.g., rate limiting)
     * use the same state for each invocation (e.g., counters or timestamps).
     * This method allows an additional qualifier to be given which allows for
     * different conditional state for each unique qualifier.
     *
     * This only makes a difference for log statements which use persistent state
     * to control conditional behaviour (e.g., [atMostEvery] or [every]).
     *
     * This is the most general form of log aggregation and allows any keys to be used,
     * but it requires the caller to have chosen a bucketing strategy.
     * Where it is possible to refactor code to avoid passing keys from an
     * unbounded space into the [per] method (e.g., by mapping cases to an [Enum]),
     * this is usually preferable.
     *
     * When using this method, a bucketing strategy is needed to reduce
     * the risk of leaking memory. Consider the alternate API:
     *
     * ```kotlin
     * // Rate limit per unique error message ("No such file", "File corrupted" etc.).
     * logger.atWarning().per(error.getMessage()).atMostEvery(30, SECONDS).log(...)
     * ```
     *
     * A method such as the one above would need to store some record of
     * all the unique messages it has seen in order to perform aggregation.
     * This means that the API would suffer a potentially unbounded memory
     * leak if a timestamp were included in the message (since all values
     * would now be unique and need to be retained).
     *
     * To fix (or at least mitigate) this issue, a [LogPerBucketingStrategy] is
     * passed to provide a mapping from "unbounded key space" (e.g., arbitrary strings)
     * to a bounded set of "bucketed" values.
     *
     * In the case of error messages, you might implement a bucketing strategy to
     * classify error messages based on the type of error.
     *
     * This method is most useful in helping to avoid cases where a rare event
     * might never be logged due to rate limiting.
     *
     * For example, the following code will cause log statements with
     * different types of `errorMessage`s to be rate-limited independently of each other.
     *
     * ```kotlin
     * // Rate limit for each type of error (FileNotFoundException, CorruptedFileException etc.).
     * logger.atInfo().per(error, byClass()).atMostEvery(30, SECONDS).log(...)
     * ```
     *
     * If a user knows that the given [key] values really do form a strictly bounded set,
     * the [LogPerBucketingStrategy.knownBounded] strategy can be used, but it should always
     * be documented as to why this is safe.
     *
     * The [key] passed to this method should always be a variable (passing a constant value
     * has no effect). If a `null` key is passed, this call has no effect (e.g. rate limiting
     * will apply normally, without respect to any specific scope).
     *
     * If multiple aggregation keys are added to a single log statement, then they all take effect
     * and logging is aggregated by the unique combination of keys passed to all "per" methods.
     *
     * @param key The key to aggregate logging by.
     * @param strategy The bucketing strategy to use for the given key.
     */
    public fun <T> per(key: T?, strategy: LogPerBucketingStrategy<in T>): API

    /**
     * Aggregates stateful logging with respect to the given enum value.
     *
     * Normally log statements with conditional behaviour (e.g., rate limiting)
     * use the same state for each invocation (e.g., counters or timestamps).
     * This method allows an additional qualifier to be given which allows for
     * different conditional state for each unique qualifier.
     *
     * This only makes a difference for log statements which use persistent state
     * to control conditional behaviour (e.g., [atMostEvery] or [every]).
     *
     * This method is most useful in helping to avoid cases where a rare
     * event might never be logged due to rate limiting.
     *
     * For example, the following code will cause log statements with
     * different `taskType`s to be rate-limited independently of each other.
     *
     * ```kotlin
     * // We want to rate limit logging separately for all task types.
     * logger.at(INFO).per(taskType).atMostEvery(30, SECONDS).log("Start task: %s", taskSpec)
     * ```
     *
     * The [key] passed to this method should always be a variable
     * (passing a constant value has no effect).
     *
     * If `null` is passed, this call has no effect (e.g., rate limiting will
     * apply normally, without respect to any specific scope).
     *
     * If multiple aggregation keys are added to a single log statement,
     * then they all take effect and logging is aggregated by the unique
     * combination of keys passed to all "per" methods.
     *
     * @param key The key to aggregate logging by.
     */
    public fun per(key: Enum<*>?): API

    /**
     * Aggregates stateful logging with respect to a scoped context determined by the given scope
     * provider.
     *
     * When [io.spine.logging.jvm.context.ScopedLoggingContext] is used to create a context,
     * it can be bound to a [io.spine.logging.jvm.context.ScopeType]. For example:
     *
     * ```kotlin
     * ScopedLoggingContexts.newContext(REQUEST).run { scopedMethod(x, y, z) }
     * ```
     *
     * where [io.spine.logging.jvm.context.ScopeType.REQUEST] defines the scope
     * type for the context in which `scopedMethod()` is called. Within this context, the scope
     * associated with the `REQUEST` type can then be used to aggregate logging behavior:
     *
     * ```kotlin
     * logger.atInfo().atMostEvery(5, SECONDS).per(REQUEST).log("Some message...")
     * ```
     *
     * New scope types can be created for specific subtasks using
     * [ScopeType.create][io.spine.logging.jvm.context.ScopeType.create]
     * but it is recommended to use shared constants
     * (such as [ScopeType.REQUEST][io.spine.logging.jvm.context.ScopeType.REQUEST])
     * wherever feasible to avoid confusion.
     *
     * Note that in order for the request scope to be applied to a log statement,
     * the `per(REQUEST)` method must still be called;
     * just being inside the request scope isn't enough.
     *
     * Unlike other [per] methods, this method is expected to be given a constant value.
     * This is because the given value *provides* the current scope, rather than *being*
     * the current scope.
     *
     * If a log statement using this method is invoked outside a context of the given type,
     * this call has no effect (e.g., rate limiting will apply normally, without respect
     * to any specific scope).
     *
     * If multiple aggregation keys are added to a single log statement, then they all take effect
     * and logging is aggregated by the unique combination of keys passed to all "per" methods.
     *
     * @param scopeProvider A constant used to define the type of the scope in which
     *        logging is aggregated.
     */
    public fun per(scopeProvider: LoggingScopeProvider): API

    /**
     * Creates a synthetic exception and attaches it as the "cause" of the log statement
     * as a way to provide additional context for the logging call itself.
     *
     * The exception created by this method is always of the type [LogSiteStackTrace],
     * and its message indicates the stack size.
     *
     * If the [withCause] method is also called for the log statement
     * (either before or after) [withStackTrace], the given exception
     * becomes the cause of the synthetic exception.
     *
     * @param size The maximum size of the stack trace to be generated.
     */
    public fun withStackTrace(size: StackSize): API

    /**
     * Adds a metadata key-value pair to the log statement.
     *
     * ### Keys cannot be `null`
     *
     * Null keys are always bad, even if the value is also `null`.
     * This is one of the few places where the logger API will throw a runtime exception,
     * and as such it is important to ensure the `NoOp` implementation also does the check.
     *
     * The reasoning for this is that the metadata key is never expected to be
     * passed user data and should always be a static constant.
     * Because of this it is always going to be an obvious code error if we get a `null` here.
     *
     * @param key The metadata key to add.
     * @param value The value to associate with the key.
     */
    public fun <T : Any> with(key: MetadataKey<T>, value: T?): API

    /**
     * Sets a boolean metadata key constant to `true` for this log statement in a structured way
     * that is accessible to logger backends.
     *
     * This method is not a replacement for general parameter passing in the [log] method
     * and should be reserved for keys/values with specific semantics. Examples include:
     *
     * - Keys that are recognised by specific logger backends (typically to control logging
     *   behaviour in some way).
     * - Key value pairs which are explicitly extracted from logs by tools.
     *
     * This method is just an alias for `with(key, true)` to improve readability.
     *
     * @param key The boolean metadata key (expected to be a static constant).
     * @see MetadataKey
     */
    public fun with(key: MetadataKey<Boolean>): API

    /**
     * Sets the log site for the current log statement.
     *
     * Explicit log site injection is rarely necessary, since either the log site
     * is injected automatically at compile time, or it is determined at runtime
     * via stack analysis.
     *
     * The one use case where calling this method explicitly may be useful is
     * when making logging helper methods, where some common project-specific
     * logging behavior is enshrined.
     *
     * For example, one can implement the following helper:
     *
     * ```kotlin
     * import io.spine.logging.LogSite
     * import io.spine.logging.LoggingFactory
     *
     * object MyLoggingHelper {
     *
     *     private val logger = LoggingFactory.loggerFor(this::class)
     *
     *     fun logAtInfo(logSite: LogSite, msg: () -> String) = logger.atInfo()
     *         .withInjectedLogSite(logSite)
     *         .log(msg)
     * }
     * ```
     *
     * And then call it elsewhere:
     *
     * ```kotlin
     * import io.spine.logging.LogSiteLookup.logSite
     * import MyLoggingHelper.logAtInfo
     *
     * ...
     *
     * logAtInfo(logSite()) { "useful info 1" }
     * logAtInfo(logSite()) { "useful info 2" }
     * ```
     *
     * Now each of the call sites for the helper method is treated as if
     * it were in the logging API. Things like rate limiting work separately
     * for each, and the location in the log statement will be the point at
     * which the helper method was called.
     *
     * Another option is to determine an outer caller of `MyLoggingHelper.logAtInfo()`
     * from inside instead of manual passing of a log site for every statement.
     *
     * Implementation option of `MyLoggingHelper` with [LogSiteLookup.callerOf]:
     *
     * ```kotlin
     * import io.spine.logging.LogSiteLookup.callerOf
     * import io.spine.logging.LoggingFactory
     *
     * object MyLoggingHelper {
     *
     *     private val logger = LoggingFactory.loggerFor(this::class)
     *
     *     fun logAtInfo(msg: () -> String) = logger.atInfo()
     *         .withInjectedLogSite(callerOf(MyLoggingHelper::class))
     *         .log(msg)
     * }
     * ```
     *
     * Now, the log site for each call is determined automatically to be a caller
     * of `MyLoggingHelper` class, and logging statements look much prettier:
     *
     * ```kotlin
     * import MyLoggingHelper.logAtInfo
     *
     * ...
     *
     * logAtInfo { "useful info 1" }
     * logAtInfo { "useful info 2" }
     * ```
     *
     * This method must only be explicitly called once for any log statement,
     * and if this method is called multiple times, the first invocation will
     * take precedence. This is because log site injection (if present) is
     * expected to occur just before the final log() call and must be overrideable
     * by earlier (explicit) calls.
     */
    public fun withInjectedLogSite(logSite: LogSite): API

    /**
     * Internal method not for public use. This method is only intended for use by the logger
     * agent and related classes and should never be invoked manually.
     *
     * @param internalClassName Slash-separated class name obtained from the class constant pool.
     * @param methodName Method name obtained from the class constant pool.
     * @param encodedLineNumber line number and per-line log statement index
     *        encoded as a single 32-bit value.
     *        The low 16-bits is the line number (`0` to `0xFFFF` inclusive) and the high 16 bits
     *        is a log statement index to distinguish multiple statements on the same line
     *        (this becomes important if line numbers are stripped from the class file
     *        and everything appears to be on the same line).
     * @param sourceFileName Optional base name of the source file.
     *        This value is strictly for debugging and does not contribute
     *        to either `equals()` or `hashCode()` behavior.
     */
    @Internal
    public fun withInjectedLogSite(
        internalClassName: String,
        methodName: String,
        encodedLineNumber: Int,
        sourceFileName: String?
    ): API

    /**
     * An implementation of [LoggingApi] which does nothing, discarding all parameters.
     *
     * Extending classes are likely to be non-wildcard, fully specified, no-op
     * implementations of the [API].
     */
    @Suppress("TooManyFunctions")
    public open class NoOp<API: LoggingApi<API>>: LoggingApi<API> {

        /**
         * Obtains the reference to this no-op implementation of fluent logging [API].
         */
        @Suppress(
            "UNCHECKED_CAST" /* The cast is safe since the class implements `API`. */,
            "MemberNameEqualsClassName" /* The name highlights the emptiness of the impl. */
        )
        protected fun noOp(): API = this as API

        override fun withLoggingDomain(domain: LoggingDomain): API = noOp()

        override fun withCause(cause: Throwable?): API = noOp()

        override fun withInjectedLogSite(logSite: LogSite): API = noOp()

        override fun withInjectedLogSite(
            internalClassName: String,
            methodName: String,
            encodedLineNumber: Int,
            sourceFileName: String?
        ): API = noOp()

        override fun <T : Any> with(key: MetadataKey<T>, value: T?): API = noOp()

        override fun with(key: MetadataKey<Boolean>): API = noOp()

        override fun withStackTrace(size: StackSize): API = noOp()

        override fun every(n: Int): API = noOp()

        override fun onAverageEvery(n: Int): API = noOp()

        override fun per(key: Enum<*>?): API = noOp()

        override fun <T> per(key: T?, strategy: LogPerBucketingStrategy<in T>): API = noOp()

        override fun per(scopeProvider: LoggingScopeProvider): API = noOp()

        override fun atMostEvery(n: Int, unit: DurationUnit): API = noOp()

        /**
         * Always returns `false`.
         */
        override fun isEnabled(): Boolean = false

        override fun log(): Unit = Unit

        override fun log(message: () -> String?): Unit = Unit
    }
}
