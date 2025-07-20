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
 * A value type which represents the location of a single log statement.
 *
 * This class is similar to the `StackTraceElement` class but differs in one important respect.
 *
 * A LogSite can be associated with a globally unique ID, which can identify a log statement
 * more uniquely than a line number (it is possible to have multiple log statements appear
 * to be on a single line, especially for obfuscated classes).
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
 *       Original Java code of Google Flogger</a> for historical context.
 */
public abstract class JvmLogSite : LogSiteKey {

    /**
     * Returns the name of the class containing the log statement.
     */
    public abstract fun getClassName(): String

    /**
     * Returns the name of the method containing the log statement.
     */
    public abstract fun getMethodName(): String

    /**
     * Returns a valid line number for the log statement in the range `1` – `65535`, or
     * [UNKNOWN_LINE] if not known.
     *
     * There is a limit of 16 bits for line numbers in a class. See
     * <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.12">here</a>
     * for more details.
     */
    public abstract fun getLineNumber(): Int

    /**
     * Returns the name of the class file containing the log statement (or null if not known).
     *
     * The source file name is optional and strictly for debugging purposes.
     *
     * Normally this value (if present) is extracted from the SourceFile attribute of the
     * class file (see the <a
     * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.10">JVM
     * class file format specification</a> for more details).
     */
    public abstract fun getFileName(): String?

    // Provide a common toString() implementation for only the public attributes.
    override fun toString(): String {
        val out = StringBuilder()
            .append("LogSite{ class=")
            .append(getClassName())
            .append(", method=")
            .append(getMethodName())
            .append(", line=")
            .append(getLineNumber())
        
        getFileName()?.let { fileName ->
            out.append(", file=").append(fileName)
        }
        
        return out.append(" }").toString()
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
         * @param internalClassName Slash-separated class name obtained from
         *        the class constant pool.
         * @param methodName A method name obtained from the class constant pool.
         * @param encodedLineNumber The line number and per-line log statement index encoded as
         *        a single 32-bit value. The low 16-bits is the line number
         *        (`0` to `0xFFFF` inclusive) and
         *         the high 16 bits is a log statement index to distinguish multiple statements
         *         on the same line (this becomes important if line numbers are stripped from
         *         the class file and everything appears to be on the same line).
         * @param sourceFileName Optional base name of the source file (this value is strictly for
         *         debugging and does not contribute to either equals() or hashCode() behavior).
         */
        @Deprecated(
            "this method is only used for log-site injection and should not be called directly"
        )
        @JvmStatic
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
    }
}
