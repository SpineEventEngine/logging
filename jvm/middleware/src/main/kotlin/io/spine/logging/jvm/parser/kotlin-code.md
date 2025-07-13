# Kotlin Code for `io.spine.logging.jvm.parser` Package

This document contains the Kotlin code for the Java files in the `io.spine.logging.jvm.parser` package.
Follow the instructions in the README.md file to migrate the package to Kotlin.

## MessageParser.kt

```kotlin
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

package io.spine.logging.jvm.parser

/**
 * Base class from which any specific message parsers are derived (e.g. [PrintfMessageParser]
 * and [BraceStyleMessageParser]).
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/MessageParser.java)
 * for historical context.
 */
public abstract class MessageParser {
    
    public companion object {
        /**
         * The maximum allowed index (this should correspond to the MAX_ALLOWED_WIDTH
         * in [io.spine.logging.jvm.backend.FormatOptions] because at times it is ambiguous as to 
         * which is being parsed).
         */
        public const val MAX_ARG_COUNT: Int = 1000000
    }

    /**
     * Abstract parse method implemented by specific subclasses to modify parsing behavior.
     *
     * Note that when extending parsing behavior, it is expected that specific parsers such as
     * [DefaultPrintfMessageParser] or [DefaultBraceStyleMessageParser] will be
     * sub-classed. Extending this class directly is only necessary when an entirely new type of
     * format needs to be supported (which should be extremely rare).
     *
     * Implementations of this method are required to invoke the
     * [MessageBuilder.addParameterImpl] method of the supplied builder once for each
     * parameter place-holder in the message.
     */
    @Throws(ParseException::class)
    protected abstract fun <T> parseImpl(builder: MessageBuilder<T>)

    /**
     * Appends the unescaped literal representation of the given message string (assumed to be escaped
     * according to this parser's escaping rules). This method is designed to be invoked from a
     * callback method in a [MessageBuilder] instance.
     *
     * @param out the destination into which to append characters
     * @param message the escaped log message
     * @param start the start index (inclusive) in the log message
     * @param end the end index (exclusive) in the log message
     */
    public abstract fun unescape(out: StringBuilder, message: String, start: Int, end: Int)
}
```

## ParseException.kt

```kotlin
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

package io.spine.logging.jvm.parser

import java.io.Serializable

/**
 * The exception that should be thrown whenever parsing of a log message fails. This exception must
 * not be thrown outside of template parsing.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/ParseException.java)
 * for historical context.
 */
public class ParseException private constructor(errorMessage: String) : RuntimeException(errorMessage), Serializable {

    /**
     * Disable expensive stack analysis because the parse exception contains everything it needs to
     * point the user at the proximal cause in the log message itself, and backends must always
     * wrap this in a [io.spine.logging.jvm.backend.LoggingException] if they do throw it up
     * into user code (not recommended).
     */
    @Synchronized
    override fun fillInStackTrace(): Throwable = this

    public companion object {
        /**
         * The prefix/suffix to show when an error snippet is truncated (eg, "...ello [%Q] Worl...").
         * If the snippet starts or ends the message then no ellipsis is shown (e.g., "...ndex=[%Q]").
         */
        private const val ELLIPSIS = "..."

        /**
         * The length of the snippet to show before and after the error. Fewer characters will be shown
         * if the error is near the start/end of the log message and more characters will be shown if
         * adding the ellipsis would have made things longer. The maximum prefix/suffix of the snippet
         * is (SNIPPET_LENGTH + ELLIPSIS.length()).
         */
        private const val SNIPPET_LENGTH = 5

        private const val serialVersionUID = 0L

        /**
         * Creates a new parse exception for situations in which both the start and end positions of the
         * error are known.
         *
         * @param errorMessage the user error message.
         * @param logMessage the original log message.
         * @param start the index of the first character in the invalid section of the log message.
         * @param end the index after the last character in the invalid section of the log message.
         * @return the parser exception.
         */
        @JvmStatic
        public fun withBounds(
            errorMessage: String, logMessage: String, start: Int, end: Int
        ): ParseException {
            return ParseException(msg(errorMessage, logMessage, start, end))
        }

        /**
         * Creates a new parse exception for situations in which the position of the error is known.
         *
         * @param errorMessage the user error message.
         * @param logMessage the original log message.
         * @param position the index of the invalid character in the log message.
         * @return the parser exception.
         */
        @JvmStatic
        public fun atPosition(errorMessage: String, logMessage: String, position: Int): ParseException {
            return ParseException(msg(errorMessage, logMessage, position, position + 1))
        }

        /**
         * Creates a new parse exception for situations in which only the start position of the error
         * is known.
         *
         * @param errorMessage the user error message.
         * @param logMessage the original log message.
         * @param start the index of the first character in the invalid section of the log message.
         * @return the parser exception.
         */
        @JvmStatic
        public fun withStartPosition(
            errorMessage: String, logMessage: String, start: Int
        ): ParseException {
            return ParseException(msg(errorMessage, logMessage, start, -1))
        }

        /**
         * Creates a new parse exception for cases where position is not relevant.
         *
         * @param errorMessage the user error message.
         */
        @JvmStatic
        internal fun generic(errorMessage: String): ParseException {
            return ParseException(errorMessage)
        }

        /**
         * Helper to format a human-readable error message for this exception.
         */
        private fun msg(errorMessage: String, logMessage: String, errorStart: Int, errorEnd: Int): String {
            var end = errorEnd
            if (end < 0) {
                end = logMessage.length
            }
            val out = StringBuilder(errorMessage).append(": ")
            if (errorStart > SNIPPET_LENGTH + ELLIPSIS.length) {
                out.append(ELLIPSIS)
                   .append(logMessage, errorStart - SNIPPET_LENGTH, errorStart)
            } else {
                out.append(logMessage, 0, errorStart)
            }
            out.append('[')
               .append(logMessage.substring(errorStart, end))
               .append(']')
            if (logMessage.length - end > SNIPPET_LENGTH + ELLIPSIS.length) {
                out.append(logMessage, end, end + SNIPPET_LENGTH)
                   .append(ELLIPSIS)
            } else {
                out.append(logMessage, end, logMessage.length)
            }
            return out.toString()
        }
    }
}
```

## MessageBuilder.kt

```kotlin
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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.TemplateContext
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.util.Checks

/**
 * A builder which is used during message parsing to create a message object which encapsulates
 * all the formatting requirements of a log message. One message builder is created for each log
 * message that's parsed.
 *
 * @param T The message type being built.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/MessageBuilder.java)
 * for historical context.
 */
public abstract class MessageBuilder<T>(context: TemplateContext) {

    private val context: TemplateContext = Checks.checkNotNull(context, "context")

    // Mask of parameter indexes seen during parsing, used to determine if there are gaps in the
    // specified parameters (which is a parsing error).
    // This could be a long if we cared about tracking up to 64 parameters, but I suspect we don't.
    private var pmask: Int = 0

    // The maximum argument index referenced by the formatted message (only valid after parsing).
    private var maxIndex: Int = -1

    /** Returns the parser used to process the log format message in this builder. */
    public fun getParser(): MessageParser {
        return context.parser
    }

    /** Returns the log format message to be parsed by this builder. */
    public fun getMessage(): String {
        return context.message
    }

    /**
     * Returns the expected number of arguments to be formatted by this message. This is only valid
     * once parsing has completed successfully.
     */
    public fun getExpectedArgumentCount(): Int {
        return maxIndex + 1
    }

    /**
     * Called by parser implementations to signify that the parsing of the next parameter is
     * complete.
     * This method will call [addParameterImpl] with exactly the same
     * arguments, but may also do additional work before or after that call.
     *
     * @param termStart the index of the first character in the log message string that was parsed to
     *        form the given parameter.
     * @param termEnd the index after the last character in the log message string that was parsed to
     *        form the given parameter.
     * @param param a parameter representing the format specified by the substring of the log message
     *        in the range `[termStart, termEnd)`.
     */
    @Suppress("MagicNumber")
    public fun addParameter(termStart: Int, termEnd: Int, param: Parameter) {
        // Set a bit in the parameter mask according to which parameter was referenced.
        // Shifting wraps, so we must do a check here.
        if (param.index < 32) {
            pmask = pmask or (1 shl param.index)
        }
        maxIndex = maxOf(maxIndex, param.index)
        addParameterImpl(termStart, termEnd, param)
    }

    /**
     * Adds the specified parameter to the format instance currently being built. This method is to
     * signify that the parsing of the next parameter is complete.
     *
     * Note that each successive call to this method during parsing will specify a disjoint ranges of
     * characters from the log message and that each range will be higher that the previously
     * specified one.
     *
     * @param termStart the index of the first character in the log message string that was parsed to
     *        form the given parameter.
     * @param termEnd the index after the last character in the log message string that was parsed to
     *        form the given parameter.
     * @param param a parameter representing the format specified by the substring of the log message
     *        in the range `[termStart, termEnd)`.
     */
    protected abstract fun addParameterImpl(termStart: Int, termEnd: Int, param: Parameter)

    /**
     * Returns the implementation-specific result of parsing the current log message.
     */
    protected abstract fun buildImpl(): T

    /**
     * Builds a log message using the current message context.
     *
     * @return the implementation-specific result of parsing the current log message.
     */
    @Suppress("UNCHECKED_CAST")
    public fun build(): T {
        // We need to use reflection to call the protected parseImpl method
        val parseImplMethod = MessageParser::class.java.getDeclaredMethod("parseImpl", MessageBuilder::class.java)
        parseImplMethod.isAccessible = true
        parseImplMethod.invoke(getParser(), this)
        
        // There was a gap in the parameters if either:
        // 1) the mask had a gap, e.g., `..00110111`
        // 2) there were more than 32 parameters, and the mask was not full.
        // Gaps above the 32nd parameter are not detected.
        if ((pmask and (pmask + 1)) != 0 || (maxIndex > 31 && pmask != -1)) {
            val firstMissing = Integer.numberOfTrailingZeros(pmask.inv())
            throw ParseException.generic(
                String.format("unreferenced arguments [first missing index=%d]", firstMissing)
            )
        }
        return buildImpl()
    }
}
```

## PrintfMessageParser.kt

```kotlin
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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * A message parser for printf-like formatting. This parser handles format specifiers of the form
 * "%\[flags\]\[width\]\[.precision\]conversion".
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/PrintfMessageParser.java)
 * for historical context.
 */
public abstract class PrintfMessageParser : MessageParser() {

    /**
     * Returns the system line separator, or a sensible default if it cannot be determined.
     */
    @Suppress("ReturnCount")
    private fun getSafeSystemNewline(): String {
        try {
            val separator = System.getProperty("line.separator")
            if (separator != null && separator.isNotEmpty()) {
                return separator
            }
        } catch (e: SecurityException) {
            // Fall through to default.
        }
        // This is the most likely system newline character. We're doing this to avoid just returning
        // "\n", which is not correct on Windows systems.
        return "\n"
    }

    /**
     * Parses a printf term of the form "%\[flags\]\[width\]\[.precision\]conversion".
     *
     * @param builder the message builder
     * @param index the argument index for the parsed term
     * @param message the message being parsed
     * @param termStart the start of the term (the index of the '%' character)
     * @param specStart the start of the format specification (after any argument index)
     * @param formatStart the index of the format character
     * @return the index after the end of the term
     */
    @Suppress("ReturnCount")
    protected fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int {
        if (formatStart >= message.length) {
            throw ParseException.withStartPosition(
                "missing format specifier", message, termStart
            )
        }
        val formatChar = message[formatStart]
        if (formatChar == '%') {
            // Literal '%' which doesn't consume any arguments.
            builder.addParameter(
                termStart, formatStart + 1,
                SimpleParameter(FormatOptions.getDefault(), -1, "%%")
            )
            return formatStart + 1
        }
        if (formatChar == 'n') {
            // System newline which doesn't consume any arguments.
            builder.addParameter(
                termStart, formatStart + 1,
                SimpleParameter(FormatOptions.getDefault(), -1, getSafeSystemNewline())
            )
            return formatStart + 1
        }
        val options = FormatOptions.parse(message, specStart, formatStart)
        val param = getParameter(index, formatChar, options)
        builder.addParameter(termStart, formatStart + 1, param)
        return formatStart + 1
    }

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapePrintf(out, message, start, end)
    }

    @Throws(ParseException::class)
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.getMessage()
        var pos = 0
        var index = 0
        while (pos < message.length) {
            val termStart = nextPrintfTerm(message, pos)
            if (termStart == -1) {
                break
            }
            // Append everything from the current position up to the term start.
            if (termStart > pos) {
                builder.addParameter(
                    pos, termStart,
                    SimpleParameter(FormatOptions.getDefault(), -1, message.substring(pos, termStart))
                )
            }
            // Skip over the '%' character.
            var specStart = termStart + 1
            // Look for argument indices of the form '%n$X' (this is not quite the full grammar).
            if (specStart < message.length) {
                var argIndexEnd = specStart
                while (argIndexEnd < message.length && message[argIndexEnd].isDigit()) {
                    argIndexEnd++
                }
                if (argIndexEnd > specStart
                    && argIndexEnd < message.length
                    && message[argIndexEnd] == '$'
                ) {
                    try {
                        val argIndex = message.substring(specStart, argIndexEnd).toInt() - 1
                        if (argIndex < 0) {
                            throw ParseException.withBounds(
                                "invalid format argument index", message, specStart, argIndexEnd
                            )
                        }
                        index = argIndex
                        // Skip over the "n$" part to get to the format specification.
                        specStart = argIndexEnd + 1
                    } catch (e: NumberFormatException) {
                        throw ParseException.withBounds(
                            "invalid format argument index", message, specStart, argIndexEnd
                        )
                    }
                }
            }
            val formatStart = findFormatChar(message, termStart, specStart)
            pos = parsePrintfTerm(builder, index++, message, termStart, specStart, formatStart)
        }
        // Final part (will do nothing if pos >= message.length())
        if (pos < message.length) {
            builder.addParameter(
                pos, message.length,
                SimpleParameter(FormatOptions.getDefault(), -1, message.substring(pos))
            )
        }
    }

    /**
     * Returns the index of the next printf term in a message or -1 if not found.
     */
    @Suppress("ReturnCount")
    public fun nextPrintfTerm(message: String, pos: Int): Int {
        var index = pos
        while (index < message.length) {
            if (message[index] == '%') {
                // Check if we have "%%" in which case we need to skip over the first one.
                if (index + 1 < message.length && message[index + 1] == '%') {
                    index += 2
                    continue
                }
                return index
            }
            index++
        }
        return -1
    }

    /**
     * Returns the index of the format character in a printf term.
     */
    private fun findFormatChar(message: String, termStart: Int, pos: Int): Int {
        var index = pos
        while (index < message.length) {
            val c = message[index]
            if (c.isLetter() || c == '%') {
                return index
            }
            index++
        }
        // We hit the end of the message without finding a format character.
        return index
    }

    /**
     * Unescapes a printf style message, which just means replacing %% with %.
     */
    @Suppress("ReturnCount")
    private fun unescapePrintf(out: StringBuilder, message: String, start: Int, end: Int) {
        var pos = start
        while (pos < end) {
            val termStart = nextPrintfTerm(message, pos)
            if (termStart == -1 || termStart >= end) {
                break
            }
            // Append everything from the current position up to the term start.
            if (termStart > pos) {
                out.append(message, pos, termStart)
            }
            // Skip over the '%' character.
            pos = termStart + 1
            // If we have "%%" we need to output a single '%' and continue.
            if (pos < end && message[pos] == '%') {
                out.append('%')
                pos++
                continue
            }
            // Otherwise we have a real format specifier, which we don't want to unescape.
            out.append('%')
        }
        // Final part (will do nothing if pos >= end).
        if (pos < end) {
            out.append(message, pos, end)
        }
    }

    /**
     * Returns a parameter instance for the given printf format specification.
     *
     * @param index the argument index for the parameter
     * @param formatChar the format character (e.g. 'd' or 's')
     * @param options the parsed format options
     */
    protected abstract fun getParameter(index: Int, formatChar: Char, options: FormatOptions): Parameter
}
```

## BraceStyleMessageParser.kt

```kotlin
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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * A message parser for brace style formatting (e.g. "{0} {1}").
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/BraceStyleMessageParser.java)
 * for historical context.
 */
public abstract class BraceStyleMessageParser : MessageParser() {

    @Throws(ParseException::class)
    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.getMessage()
        var pos = 0
        while (pos < message.length) {
            val termStart = nextBraceFormatTerm(message, pos)
            if (termStart == -1) {
                break
            }
            // Append everything from the current position up to the term start.
            if (termStart > pos) {
                builder.addParameter(
                    pos, termStart,
                    SimpleParameter(FormatOptions.getDefault(), -1, message.substring(pos, termStart))
                )
            }
            // Skip over the '{' character.
            var specStart = termStart + 1
            // Look for the terminating '}' character.
            var specEnd = specStart
            while (specEnd < message.length && message[specEnd] != '}') {
                specEnd++
            }
            if (specEnd >= message.length) {
                throw ParseException.withStartPosition(
                    "unterminated brace format", message, termStart
                )
            }
            // Extract the index from the specification.
            val indexStr = message.substring(specStart, specEnd)
            try {
                val index = indexStr.toInt()
                if (index < 0) {
                    throw ParseException.withBounds(
                        "invalid index", message, specStart, specEnd
                    )
                }
                if (index >= MAX_ARG_COUNT) {
                    throw ParseException.withBounds(
                        "index too large", message, specStart, specEnd
                    )
                }
                val param = getParameter(index)
                builder.addParameter(termStart, specEnd + 1, param)
            } catch (e: NumberFormatException) {
                throw ParseException.withBounds(
                    "invalid index", message, specStart, specEnd
                )
            }
            pos = specEnd + 1
        }
        // Final part (will do nothing if pos >= message.length())
        if (pos < message.length) {
            builder.addParameter(
                pos, message.length,
                SimpleParameter(FormatOptions.getDefault(), -1, message.substring(pos))
            )
        }
    }

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapeBraceFormat(out, message, start, end)
    }

    /**
     * Returns the index of the next brace term in a message or -1 if not found.
     */
    @Suppress("ReturnCount")
    public fun nextBraceFormatTerm(message: String, pos: Int): Int {
        var index = pos
        while (index < message.length) {
            if (message[index] == '{') {
                // Check if we have "{{" in which case we need to skip over the first one.
                if (index + 1 < message.length && message[index + 1] == '{') {
                    index += 2
                    continue
                }
                return index
            }
            if (message[index] == '}') {
                // Check if we have "}}" in which case we need to skip over the first one.
                if (index + 1 < message.length && message[index + 1] == '}') {
                    index += 2
                    continue
                }
                throw ParseException.atPosition(
                    "unmatched closing brace", message, index
                )
            }
            index++
        }
        return -1
    }

    /**
     * Unescapes a brace format message, which just means replacing {{ with { and }} with }.
     */
    @Suppress("ReturnCount")
    private fun unescapeBraceFormat(out: StringBuilder, message: String, start: Int, end: Int) {
        var pos = start
        while (pos < end) {
            val c = message[pos]
            if (c == '{' || c == '}') {
                // Check if we have "{{" or "}}" in which case we need to output just one.
                if (pos + 1 < end && message[pos + 1] == c) {
                    out.append(c)
                    pos += 2
                    continue
                }
                // Otherwise we have a real format specifier, which we don't want to unescape.
                if (c == '{') {
                    val termStart = pos
                    // Skip over the '{' character.
                    pos++
                    // Look for the terminating '}' character.
                    while (pos < end && message[pos] != '}') {
                        pos++
                    }
                    if (pos >= end) {
                        throw ParseException.withStartPosition(
                            "unterminated brace format", message, termStart
                        )
                    }
                    // Skip over the '}' character.
                    pos++
                    continue
                }
                throw ParseException.atPosition(
                    "unmatched closing brace", message, pos
                )
            }
            out.append(c)
            pos++
        }
    }

    /**
     * Returns a parameter instance for the given brace format specification.
     *
     * @param index the argument index for the parameter
     */
    protected abstract fun getParameter(index: Int): Parameter
}
```

## DefaultPrintfMessageParser.kt

```kotlin
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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.BraceStyleParameter
import io.spine.logging.jvm.parameter.DateTimeParameter
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * Default implementation of the printf message parser.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/DefaultPrintfMessageParser.java)
 * for historical context.
 */
public class DefaultPrintfMessageParser private constructor() : PrintfMessageParser() {

    override fun getParameter(index: Int, formatChar: Char, options: FormatOptions): Parameter {
        // Handle %c and %C specifically (they are the only format specifiers which need to be
        // processed differently from normal printf formatting).
        if (formatChar == 'c' || formatChar == 'C') {
            return SimpleParameter(options, index, "%${options.getUnderlyingFlags()}${formatChar}")
        }
        // Handle %t and %T specifically (date/time formatting).
        if (formatChar == 't' || formatChar == 'T') {
            return DateTimeParameter(options, index, formatChar)
        }
        // Everything else is just passed through to the formatter.
        val formatType = FormatChar.of(formatChar)
        return if (formatType != null) {
            SimpleParameter(options, index, "%${options.getUnderlyingFlags()}${formatChar}")
        } else {
            // This is a non-standard format specifier (e.g. %q) which won't be parsed by the
            // standard formatter, so we need to handle it differently. We know that the format
            // specifier is a letter (that's enforced by the parser) so we can just pass it as a
            // simple parameter.
            BraceStyleParameter(options, index)
        }
    }

    public companion object {
        private val INSTANCE = DefaultPrintfMessageParser()

        @JvmStatic
        public fun getInstance(): DefaultPrintfMessageParser {
            return INSTANCE
        }
    }
}
```

## DefaultBraceStyleMessageParser.kt

```kotlin
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

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.BraceStyleParameter
import io.spine.logging.jvm.parameter.Parameter

/**
 * Default implementation of the brace style message parser.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/DefaultBraceStyleMessageParser.java)
 * for historical context.
 */
public class DefaultBraceStyleMessageParser private constructor() : BraceStyleMessageParser() {

    override fun getParameter(index: Int): Parameter {
        return BraceStyleParameter(FormatOptions.getDefault(), index)
    }

    public companion object {
        private val INSTANCE = DefaultBraceStyleMessageParser()

        @JvmStatic
        public fun getInstance(): DefaultBraceStyleMessageParser {
            return INSTANCE
        }
    }
}
```
