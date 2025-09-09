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

package io.spine.logging

import com.google.errorprone.annotations.RestrictedApi
import io.spine.annotation.Internal

/**
 * Creates a log site injected from constants held in a class' constant pool.
 *
 * Used for compile-time log site injection, and by the agent.
 *
 * @param internalClassName Internal, slash-separated, fully-qualified class name
 *        (e.g., `"com/example/Foo$Bar"`).
 * @param methodName Bare method name without signature information.
 * @param encodedLineNumber line number and per-line log statement index encoded as a single
 *        32-bit value. The low 16-bits is the line number (`0` to `0xFFFF` inclusive) and
 *        the high 16 bits is a log statement index to distinguish multiple statements on
 *        the same line. This becomes important if line numbers are stripped from the class
 *        file and everything appears to be on the same line.
 * @param sourceFileName Optional name of the source file containing the log statement.
 */
@Internal
@RestrictedApi(
    explanation =
        "This function is only used for log-site injection and should not be called directly.",
    allowlistAnnotations = [LogSiteInjector::class]
)
public fun injectedLogSite(
    internalClassName: String,
    methodName: String,
    encodedLineNumber: Int,
    sourceFileName: String?
): LogSite = InjectedLogSite(
    internalClassName,
    methodName,
    encodedLineNumber,
    sourceFileName
)

/**
 * A log site implementation used for injected logging information during compile-time or
 * via agents.
 *
 * This class maintains information about the location of a log statement, including the
 * class name, method name, line number, and source file.
 *
 * The class name is stored in the internal JVM format (slash-separated) and converted to
 * the standard dot-separated format when needed for proper identification.
 *
 * @property internalClassName an internal, slash-separated, fully-qualified class name
 *           (e.g., `"com/example/Foo$Bar"`).
 * @property method a bare method name without signature information.
 * @property encodedLineNumber a line number and per-line log statement index encoded as a single
 *           32-bit value. The low 16-bits is the line number (`0` to `0xFFFF` inclusive) and
 *           the high 16 bits is a log statement index to distinguish multiple statements on
 *           the same line. This becomes important if line numbers are stripped from the class
 *           file and everything appears to be on the same line.
 * @param sourceFileName The name of the source file containing the log statement.
 * @see LoggingApi.withInjectedLogSite
 */
internal class InjectedLogSite(
    private val internalClassName: String,
    private val method: String,
    private val encodedLineNumber: Int,
    sourceFileName: String?
) : LogSite() {

    /**
     * Cached hash code value for optimized lookups.
     */
    @Volatile
    private var hashcode = 0

    /**
     * Obtains dot-separated class name.
     *
     * ## Implementation note
     *
     * We have to do the conversion from internal to public class name somewhere, and doing
     * it earlier could cost work in cases where the log statement is dropped.
     *
     * We could cache the result somewhere, but in the default logger backend, this method
     * is actually only called once anyway when constructing the `LogRecord` instance.
     */
    override val className: String
        get() = internalClassName.replace('/', '.')

    override val methodName: String = method

    /**
     * Strips additional "uniqueness" information from the upper 16 bits.
     */
    @Suppress("MagicNumber")
    override val lineNumber: Int
        get() = encodedLineNumber and 0xFFFF

    override val fileName: String? = sourceFileName

    override fun equals(other: Any?): Boolean {
        if (other is InjectedLogSite) {
            // Probably not worth optimizing for "this === other" because
            // all strings should be interned.
            return method == other.method &&
                encodedLineNumber == other.encodedLineNumber &&
                // Check classname last because it isn't cached.
                className == other.className
        }
        return false
    }

    override fun hashCode(): Int {
        if (hashcode == 0) {
            // TODO(dbeaumont): Revisit the algorithm when looking at b/22753674.
            // If the log statement uses metadata, the log site will be used as a key to look up the
            // current value. In most cases the hashcode is never needed, but in others it may be used
            // multiple times in different data structures.
            var temp = 157

            // Don't include classname since it isn't cached. Other fields should be unique enough.
            temp = 31 * temp + methodName.hashCode()
            temp = 31 * temp + encodedLineNumber
            hashcode = temp
        }
        return hashcode
    }
}
