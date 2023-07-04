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

package io.spine.logging

import kotlin.time.DurationUnit

/**
 * The basic logging API returned by a [Logger].
 */
public interface LoggingApi<API: LoggingApi<API>> {

    /**
     * Associates the given domain with the current log statement.
     */
    public fun withLoggingDomain(domain: LoggingDomain): API

    /**
     * Associates a [Throwable] with the current log statement.
     *
     * Presumably, this method is called from withing a catch block to
     * log a caught exception. If the `cause` is null, the method has no effect.
     *
     * If the method is called more than once, the parameter of the last
     * invocation will be used.
     */
    public fun withCause(cause: Throwable): API

    public fun withInjectedLogSite(logSite: LogSite): API

    /**
     * Modifies the current log statement to be emitted only once per N invocations.
     *
     * The specified `n` must be greater than zero.
     *
     * The first invocation of this rate-limited log statement is always emitted.
     *
     * ## Notes
     *
     * 1. If `atMostEvery(...)` and `every(...)` are invoked for the same statement,
     * then it will be emitted only when both criteria are satisfied.
     * Firstly, `atMostEvery(...)` criterion should be satisfied, then the counter
     * for `every(...)` starts. As `every(...)` becomes satisfied, the statement
     * is emitted.
     *
     * 2. If this method is called multiple times for a single log statement,
     * the last invocation will take precedence.
     *
     * @throws IllegalArgumentException
     *          if `n` is negative or zero
     */
    public fun every(n: Int): API

    /**
     * Modifies the current log statement to be emitted **at most** once per
     * the specified period.
     *
     * The passed `n` must be non-negative.
     *
     * The first invocation of this rate-limited log statement is always emitted.
     *
     * ## Behavior
     *
     * A call to this method adds the following pre-condition for emitting
     * the log statement:
     *
     * ```
     * val currentTimestampNanos = now()
     * val lastTimestampNanos = ... // The last time this statement was emitted.
     * val rateNanos = n.toDuration(unit).inWholeNanoseconds // The specified rate.
     * val shouldEmit = currentTimestampNanos >= lastTimestampNanos + rateNanos
     * if (shouldEmit) {
     *     // The statement can be emitted. It is within the rate.
     * }
     * ```
     *
     * The effect of this is that when logging invocation is relatively infrequent,
     * the period between emitted log statements can be higher than the specified duration.
     * For example, if the following log statement was called every 600ms:
     *
     * ```
     * logger.atFine()
     *       .atMostEvery(2, SECONDS)
     *       .log { ... }
     * ```
     *
     * logging would occur after 0s, 2.4s and 4.8s (not 4.2s), giving an effective
     * duration of 2.4s between log statements over time.
     *
     * Providing a zero length duration (`n` == 0) does not affect a log statement.
     * Previously configured rate limitation is used, if any. Such a call should be
     * considered as a no-op.
     *
     * ## Granularity
     *
     * Because the implementation of this feature relies on a nanosecond timestamp
     * provided by the backend, the actual granularity of the underlying clock
     * used may vary. Thus, it is possible to specify a period smaller than
     * the smallest visible time increment. If this occurs, then the effective
     * rate limit will be the smallest available time increment. For example,
     * if the system clock granularity is 1 millisecond, and a log statement
     * is called with `atMostEvery(700, MICROSECONDS)`, the effective rate of
     * logging could never be more than once every millisecond.
     *
     * ## Notes
     *
     * 1. If `atMostEvery(...)` and `every(...)` are invoked for the same statement,
     * then it will be emitted only when both criteria are satisfied.
     * Firstly, `atMostEvery(...)` criterion should be satisfied, then the counter
     * for `every(...)` starts. As `every(...)` becomes satisfied, the statement
     * is emitted.
     *
     * 2. If this method is called multiple times for a single log statement,
     * the last invocation will take precedence.
     *
     * @throws IllegalArgumentException
     *          if `n` is negative
     */
    public fun atMostEvery(n: Int, unit: DurationUnit): API

    /**
     * Aggregates stateful logging with respect to the given enum value.
     *
     * Normally log statements with conditional behavior (e.g., rate limiting)
     * use the same state for each invocation (e.g., counters or timestamps).
     * This method allows an additional qualifier to be given, which allows
     * a different conditional state for each unique enum value.
     *
     * This only makes a difference for log statements, which use persistent
     * state to control conditional behaviour (e.g., `atMostEvery()` or `every()`).
     *
     * It is most useful in helping to avoid cases where a rare event might
     * never be logged due to rate limiting.
     *
     * For example, the following code will cause log statements with different
     * `taskType`s to be rate-limited independently of each other:
     *
     * ```
     * // We want to rate limit logging separately for each task type.
     * logger.at(INFO)
     *       .per(task.type)
     *       .atMostEvery(30, SECONDS)
     *       .log { "Task started: ${task.name}." }
     * ```
     *
     * The `key` passed to this method should always be a variable.
     * Passing of a constant has no effect.
     *
     * If multiple aggregation keys are added to a single log statement,
     * then they all take effect and logging is aggregated by the unique
     * combination of keys passed to all [per] methods.
     */
    public fun per(key: Enum<*>): API

    /**
     * Returns `true` if logging is enabled at the level implied for this API.
     */
    public fun isEnabled(): Boolean

    /**
     * Terminal log statement when a message is not required.
     *
     * For example:
     * ```kotlin
     * logger.at(ERROR).withCause(error).log()
     * ```
     */
    public fun log()

    /**
     * Logs a message produced by the given function block.
     */
    public fun log(message: () -> String)

    /**
     * An implementation of [LoggingApi] which does nothing, discarding all parameters.
     *
     * Extending classes are likely to be non-wildcard, fully specified, no-op
     * implementations of the [API].
     */
    public open class NoOp<API: LoggingApi<API>>: LoggingApi<API> {

        /**
         * Obtains the reference to this no-op implementation of fluent logging [API].
         */
        @Suppress(
            "UNCHECKED_CAST" /* The cast is safe since the class implements `API`. */,
            "MemberNameEqualsClassName" /* The name highlights the emptiness of the impl. */
        )
        protected fun noOp(): API = this as API

        /**
         * Does nothing.
         */
        override fun withLoggingDomain(domain: LoggingDomain): API = noOp()

        /**
         * Does nothing.
         */
        override fun withCause(cause: Throwable): API = noOp()

        /**
         * Does nothing.
         */
        override fun withInjectedLogSite(logSite: LogSite): API = noOp()

        /**
         * Does nothing.
         */
        override fun every(n: Int): API = noOp()

        /**
         * Does nothing.
         */
        override fun per(key: Enum<*>): API = noOp()

        /**
         * Does nothing.
         */
        override fun atMostEvery(n: Int, unit: DurationUnit): API = noOp()

        /**
         * Always returns `false`.
         */
        override fun isEnabled(): Boolean = false

        /**
         * Does nothing.
         */
        override fun log(): Unit = Unit

        /**
         * Does nothing.
         */
        override fun log(message: () -> String): Unit = Unit
    }
}
