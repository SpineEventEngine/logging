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

import com.google.errorprone.annotations.RestrictedApi
import io.spine.annotation.Internal
import io.spine.logging.jvm.JvmLogSite.Companion.UNKNOWN_LINE
import io.spine.logging.jvm.JvmLogSite.Companion.callerOf
import io.spine.logging.jvm.JvmLogSite.Companion.invalid
import io.spine.logging.jvm.JvmLogSite.Companion.logSite
import io.spine.logging.jvm.backend.Platform

/**
 * A value type which represents the location of a single log statement.
 *
 * This class is similar to the `StackTraceElement` class but differs in one important respect.
 *
 * A LogSite can be associated with a globally unique ID, which can identify a log statement
 * more uniquely than a line number. It is possible to have multiple log statements appear
 * to be on a single line, especially for obfuscated classes.
 *
 * Log sites are intended to be injected into code automatically, typically via some form of
 * bytecode rewriting. Each injection mechanism can have its own implementation of `LogSite`
 * adapted to its specific needs.
 *
 * As a fallback, for cases where no injection mechanism is configured, a log site based upon
 * stack trace analysis is used. However, due to limitations in the information available
 * from `StackTraceElement`, this log site will not be unique if multiple log statements are
 * on the same line, or if line number information was stripped from the class file.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/LogSite.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public abstract class JvmLogSite : LogSiteKey {

    /**
     * The name of the class containing the log statement.
     */
    public abstract val className: String

    /**
     * The name of the method containing the log statement.
     */
    public abstract val methodName: String

    /**
     * A valid line number for the log statement in the range `1 – 65535`, or
     * [UNKNOWN_LINE] if not known.
     *
     * There is a limit of 16 bits for line numbers in a class.
     * See [The LineNumberTable Attribute][line-number-table] for more details.
     *
     * [line-number-table]: http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.12
     */
    public abstract val lineNumber: Int

    /**
     * The name of the class file containing the log statement (or `null` if not known).
     *
     * The source file name is optional and strictly for debugging purposes.
     *
     * Normally this value (if present) is extracted from the `SourceFile` attribute of the
     * class file.
     *
     * See the [JVM class file format specification](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.10)
     * for more details.
     */
    public abstract val fileName: String?

    /**
     * Provides debug information with only the public attributes.
     */
    override fun toString(): String = buildString {
        append("LogSite{ class=")
        append(className)
        append(", method=")
        append(methodName)
        append(", line=")
        append(lineNumber)

        fileName?.let { fileName ->
            append(", file=").append(fileName)
        }

        append(" }")
    }

    public companion object {

        /**
         * A value used for line numbers when the true information is not available.
         */
        public const val UNKNOWN_LINE: Int = 0

        /**
         * A singleton LogSite instance used to indicate that valid log site information
         * cannot be determined.
         */
        @JvmField
        public val invalid: JvmLogSite = InvalidLogSite()

        /**
         * Creates a log site injected from constants held in a class' constant pool.
         *
         * Used for compile-time log site injection, and by the agent.
         *
         * @param internalClassName Slash-separated class name obtained from the class constant pool.
         * @param methodName A method name obtained from the class constant pool.
         * @param encodedLineNumber The line number and per-line log statement index encoded as
         *        a single 32-bit value. The low 16-bits is the line number (`0` to `0xFFFF`
         *        inclusive) and the high 16 bits is a log statement index to distinguish
         *        multiple statements on the same line. This becomes important if line numbers
         *        are stripped from the class file and everything appears to be on the same line.
         * @param sourceFileName Optional base name of the source file. This value is strictly
         *        for debugging and does not contribute to either `equals()` or `hashCode()`
         *        behavior.
         */
        @JvmStatic
        @Internal
        @RestrictedApi(
            explanation = "This method is only used for log-site" +
                    " injection and should not be called directly.",
            allowlistAnnotations = [LogSiteInjector::class]
        )
        public fun injectedLogSite(
            internalClassName: String,
            methodName: String,
            encodedLineNumber: Int,
            sourceFileName: String?
        ): JvmLogSite = InjectedJvmLogSite(
            internalClassName,
            methodName,
            encodedLineNumber,
            sourceFileName
        )

        /**
         * Returns a [JvmLogSite] for the caller of the specified class.
         *
         * This can be used in conjunction with the [MiddlemanApi.withInjectedLogSite] method to
         * implement logging helper methods. In some platforms, log site determination may be
         * unsupported, and in those cases this method will always return the [invalid] instance.
         *
         * For example (in `MyLoggingHelper`):
         * ```kotlin
         * fun logAndSomethingElse(String message, Object... args) {
         *   logger.atInfo()
         *       .withInjectedLogSite(callerOf(MyLoggingHelper.class))
         *       .logVarargs(message, args)
         * }
         * ```
         *
         * This method should be used for the simple cases where the class in which the logging
         * occurs is a public logging API. If the log statement is in a different class (not the
         * public logging API) and the [JvmLogSite] instance needs to be passed through several
         * layers, consider using [logSite] instead to avoid too much "magic" in your code.
         *
         * You should also seek to ensure that any API used with this method "looks like a logging
         * API". It is no good if a log entry contains a class and method name which does not
         * correspond to anything the user can relate to. In particular, the API should probably
         * always accept the log message or at least some of its parameters, and should always
         * have methods with "log" in their names to make the connection clear.
         *
         * It is very important to note that this method can be very slow, since determining the
         * log site can involve stack trace analysis. It is only recommended that it is used for
         * cases where logging is expected to occur (e.g. `INFO` level or above). Implementing a
         * helper method for `FINE` logging is usually unnecessary (it doesn't normally need to
         * follow any specific "best practice" behavior).
         *
         * Note that even when log site determination is supported, it is not defined as to
         * whether two invocations of this method on the same line of code will produce the same
         * instance, equivalent instances or distinct instance. Thus you should never invoke this
         * method twice in a single statement (and you should never need to).
         *
         * Note that this method call may be replaced in compiled applications via bytecode
         * manipulation or other mechanisms to improve performance.
         *
         * @param loggingApi The logging API to be identified as the source of log statements
         *        (this must appear somewhere on the stack above the point at which this method
         *        is called).
         * @return The log site of the caller of the specified logging API, or [invalid] if the
         *        logging API was not found.
         */
        @JvmStatic
        public fun callerOf(loggingApi: Class<*>): JvmLogSite {
            // Can't skip anything here since someone could pass in `LogSite.class`.
            return Platform.getCallerFinder().findLogSite(loggingApi, 0)
        }

        /**
         * Returns a [JvmLogSite] for the current line of code.
         *
         * This can be used in conjunction with the [MiddlemanApi.withInjectedLogSite] method to
         * implement logging helper methods. In some platforms, log site determination may be
         * unsupported, and in those cases this method will always return the [invalid] instance.
         *
         * For example (in `MyLoggingHelper`):
         * ```kotlin
         * fun logAndSomethingElse(LogSite logSite, String message, Object... args) {
         *   logger.atInfo()
         *       .withInjectedLogSite(logSite)
         *       .logVarargs(message, args)
         * }
         * ```
         * where callers would do:
         * ```kotlin
         * MyLoggingHelper.logAndSomethingElse(logSite(), "message...")
         * ```
         *
         * Because this method adds an additional parameter and exposes a Flogger specific type
         * to the calling code, you should consider using [callerOf] for simple logging utilities.
         *
         * It is very important to note that this method can be very slow, since determining the
         * log site can involve stack trace analysis. It is only recommended that it is used for
         * cases where logging is expected to occur (e.g. `INFO` level or above). Implementing a
         * helper method for `FINE` logging is usually unnecessary (it doesn't normally need to
         * follow any specific "best practice" behavior).
         *
         * Note that even when log site determination is supported, it is not defined as to
         * whether two invocations of this method on the same line of code will produce the same
         * instance, equivalent instances or distinct instance. Thus you should never invoke this
         * method twice in a single statement (and you should never need to).
         *
         * Note that this method call may be replaced in compiled applications via bytecode
         * manipulation or other mechanisms to improve performance.
         *
         * @return The log site of the caller of this method.
         */
        @JvmStatic
        public fun logSite(): JvmLogSite {
            // Don't call "callerOf()" to avoid making another stack entry.
            return Platform.getCallerFinder().findLogSite(Companion::class.java, 0)
        }

        /**
         * Returns a new [JvmLogSite] which reflects the information in the given [StackTraceElement],
         * or [invalid] if given `null`.
         *
         * This method is useful when log site information is only available via an external API
         * which returns [StackTraceElement].
         */
        @JvmStatic
        public fun logSiteFrom(e: StackTraceElement?): JvmLogSite {
            return if (e != null) StackBasedLogSite(e) else invalid
        }
    }
}
