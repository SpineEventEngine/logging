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

/**
 * Enum values to be passed into [io.spine.logging.LoggingApi.withStackTrace] to control the maximum
 * number of stack trace elements created.
 *
 * @property maxDepth The maximum stack depth to create when adding contextual stack
 *   information to a log statement. Note that the precise number of stack elements
 *   emitted for the enum values might change over time, but the ordering relationship
 *   `NONE < SMALL <= MEDIUM <= LARGE <= FULL` will always be maintained.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/StackSize.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@Suppress("MagicNumber") // The numbers are the maximum stack sizes for each enum value.
public enum class StackSize(public val maxDepth: Int) {

    /**
     * Produces a small stack suitable for finer grained debugging.
     *
     * For performance reasons, this is the only stack size suitable for log statements at
     * level `INFO` or finer, but it may also be useful for `WARNING` level log statements
     * in cases where context is not as important. For `SEVERE` log statements, it is
     * advised to use a stack size of [MEDIUM] or above.
     *
     * Requesting a small stack trace for log statements which occur under normal
     * circumstances is acceptable but may affect performance. Consider using
     * [io.spine.logging.LoggingApi.withStackTrace] in conjunction with rate-limiting methods,
     * such as [io.spine.logging.LoggingApi.atMostEvery], to mitigate performance issues.
     *
     * The current maximum size of a `SMALL` stack trace is 10 elements, but this
     * value may change in future versions.
     */
    SMALL(10),

    /**
     * Produces a medium-sized stack suitable for providing contextual information.
     *
     * Suitable for most log statements at `WARNING` or above. There should be enough stack
     * trace elements in a `MEDIUM` stack to provide sufficient debugging context in most
     * cases.
     *
     * Requesting a medium stack trace for any log statements which can occur regularly
     * under normal circumstances is not recommended.
     *
     * The current maximum size of a `MEDIUM` stack trace is 20 elements, but this
     * value may change in future versions.
     */
    MEDIUM(20),

    /**
     * Produces a large stack suitable for providing highly detailed contextual information.
     *
     * This is most useful for `SEVERE` log statements which might be processed by external
     * tools and subject to automated analysis.
     *
     * Requesting a large stack trace for any log statement which can occur under normal
     * circumstances is not recommended.
     *
     * The current maximum size of a `LARGE` stack trace is 50 elements, but this
     * value may change in future versions.
     */
    LARGE(50),

    /**
     * Provides the complete stack trace.
     *
     * This is included for situations in which it is known that the uppermost elements
     * of the stack are definitely required for analysis.
     *
     * Requesting a full stack trace for any log statement which can occur under normal
     * circumstances is not recommended.
     */
    FULL(-1),

    /**
     * Provides no stack trace, making the `withStackTrace()` method an effective no-op.
     *
     * This is useful when your stack size is conditional. For example:
     * ```kotlin
     * logger.atWarning()
     *     .withStackTrace(if (showTrace) StackSize.MEDIUM else StackSize.NONE)
     *     .log("message")
     * ```
     */
    NONE(0)
}

