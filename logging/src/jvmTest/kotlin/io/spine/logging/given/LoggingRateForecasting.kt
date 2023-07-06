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

package io.spine.logging.given

import io.spine.logging.LogSite
import io.spine.logging.LoggingApi

/**
 * This file contains methods that calculate the expected logging rate
 * for test assertions.
 */

/**
 * Calculates how many times a logging statement should be executed when
 * its execution rate is limited by [LoggingApi.every] method.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param invocationRateLimit
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
internal fun expectedRuns(invocations: Int, invocationRateLimit: Int): Int {
    val sureExecutions = invocations / invocationRateLimit
    val hasRemainder = invocations % invocationRateLimit != 0
    return sureExecutions + if (hasRemainder) 1 else 0
}

/**
 * Calculates how many times a logging statement should be executed for each
 * enum value when the execution rate is limited by [LoggingApi.every] method.
 *
 * @param invocations
 *          number of times a logging statement is invoked for each enum value
 * @param invocationRateLimit
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
@JvmName("expectedRunsPerTask") // JVM has a problem with the conflicting erasures.
internal fun expectedRuns(
    invocations: Map<Task, Int>,
    invocationRateLimit: Int,
): Map<Task, Int> = invocations.mapValues { entry ->
    expectedRuns(entry.value, invocationRateLimit)
}

/**
 * Calculates how many times a logging statement should be executed for each
 * combination of enum values when the execution rate is limited by
 * [LoggingApi.every] method.
 *
 * @param invocations
 *          number of times a logging statement is invoked for each combination
 *          of enum values
 * @param invocationRateLimit
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
@JvmName("expectedRunsPerTasks") // JVM has a problem with the conflicting erasures.
internal fun expectedRuns(
    invocations: Map<Set<Task>, Int>,
    invocationRateLimit: Int,
): Map<Set<Task>, Int> = invocations.mapValues { entry ->
    expectedRuns(entry.value, invocationRateLimit)
}

/**
 * Calculates how many times a logging statement should be executed for each
 * log site in [invocations] collection, when the execution rate is limited
 * by [LoggingApi.every] method.
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
internal fun expectedRuns(invocations: InvocationsPerSite): Map<LogSite, Int>
= invocations.associate { (logSite, rate, invocations) ->
    logSite to expectedRuns(invocations, rate)
}

/**
 * Calculates how many times a logging statement should be executed when
 * its execution rate is limited by [LoggingApi.atMostEvery] method.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param intervalMillis
 *          time interval between each two invocations
 * @param intervalLimitMillis
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
internal fun expectedRuns(invocations: Int, intervalMillis: Long, intervalLimitMillis: Int): Int {
    var result = 1
    var lastInvocationMillis = 0L
    var elapsedMillis = 0L

    repeat(invocations - 1) {
        elapsedMillis += intervalMillis
        if (elapsedMillis - lastInvocationMillis >= intervalLimitMillis) {
            lastInvocationMillis = elapsedMillis
            result++
        }
    }

    return result
}

/**
 * Calculates the expected timestamps at which a logging statement should be
 * executed when [interval][LoggingApi.atMostEvery] rate limit is configured.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param intervalMillis
 *          time interval between each two invocations
 * @param intervalLimitMillis
 *          the configured rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
internal fun expectedTimestamps(
    invocations: Int,
    intervalMillis: Long,
    intervalLimitMillis: Int,
): List<Long> {

    val result = mutableListOf(0L)
    var currentMillis = 0L
    var lastLoggedMillis = 0L

    for (i in 1..invocations) {
        if (currentMillis >= lastLoggedMillis + intervalLimitMillis) {
            result.add(currentMillis)
            lastLoggedMillis = currentMillis
        }
        currentMillis += intervalMillis

    }

    return result
}

/**
 * Calculates the expected timestamps at which a logging statement should be
 * executed when [interval][LoggingApi.atMostEvery] and [invocation][LoggingApi.every]
 * rate limits are configured simultaneously.
 *
 * @param invocations
 *          number of times a logging statement is invoked
 * @param intervalMillis
 *          time interval between each two invocations
 * @param intervalLimitMillis
 *          the configured time rate limitation
 * @param invocationRateLimit
 *          the configured invocation rate limitation
 */
@Suppress("SameParameterValue") // Extracted to a method for better readability.
internal fun expectedTimestamps(
    invocations: Int,
    intervalMillis: Long,
    intervalLimitMillis: Int,
    invocationRateLimit: Int,
): List<Long> {

    val result = mutableListOf(0L)
    var currentMillis = 0L
    var lastLoggedMillis = 0L
    var invoked = 0

    for (i in 1..invocations) {
        if (currentMillis >= lastLoggedMillis + intervalLimitMillis) {
            invoked++
            if (invoked == invocationRateLimit) {
                result.add(currentMillis)
                lastLoggedMillis = currentMillis
                invoked = 0
            }
        }
        currentMillis += intervalMillis

    }

    return result
}
