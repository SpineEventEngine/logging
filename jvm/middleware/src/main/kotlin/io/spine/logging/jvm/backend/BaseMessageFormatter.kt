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

package io.spine.logging.jvm.backend

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.jvm.backend.FormatChar.BOOLEAN
import io.spine.logging.jvm.backend.FormatChar.CHAR
import io.spine.logging.jvm.backend.FormatChar.DECIMAL
import io.spine.logging.jvm.backend.FormatChar.HEX
import io.spine.logging.jvm.backend.FormatChar.STRING
import io.spine.logging.jvm.backend.FormatOptions.Companion.FLAG_UPPER_CASE
import io.spine.logging.jvm.backend.MessageUtils.FORMAT_LOCALE
import io.spine.logging.jvm.backend.MessageUtils.appendHex
import io.spine.logging.jvm.backend.MessageUtils.safeToString
import io.spine.logging.jvm.parameter.DateTimeFormat
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.ParameterVisitor
import io.spine.logging.jvm.parser.MessageBuilder
import io.spine.logging.jvm.util.Checks.checkNotNull
import java.util.Calendar
import java.util.Date
import java.util.Formattable

/**
 * The default formatter for log messages and arguments.
 *
 * This formatter can be overridden to modify the behaviour of the [ParameterVisitor]
 * methods, but this is not expected to be common. Most logger backends will only ever need 
 * to use `[appendFormattedMessage]`.
 *
 * @param context The template context containing the message pattern and metadata.
 * @param args The arguments to be formatted into the message.
 * @param out The buffer into which the formatted message is written.
 * 
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/BaseMessageFormatter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public open class BaseMessageFormatter
protected constructor(
    context: TemplateContext,
    protected val args: Array<Any?>,
    protected val out: StringBuilder
) : MessageBuilder<StringBuilder>(context), ParameterVisitor {

    // The start of the next literal subsection of the message that needs processing.
    private var literalStart = 0

    init {
        checkNotNull(args, "arguments")
        checkNotNull(out, "buffer")
    }

    override fun addParameterImpl(termStart: Int, termEnd: Int, param: Parameter) {
        parser.unescape(out, message, literalStart, termStart)
        param.accept(this, args)
        literalStart = termEnd
    }

    override fun buildImpl(): StringBuilder {
        parser.unescape(out, message, literalStart, message.length)
        return out
    }

    override fun visit(value: Any?, format: FormatChar, options: FormatOptions) {
        if (format.type.canFormat(value)) {
            appendFormatted(out, value, format, options)
        } else {
            appendInvalid(out, value, format.defaultFormatString)
        }
    }

    override fun visitDateTime(value: Any?, format: DateTimeFormat, options: FormatOptions) {
        if (value is Date || value is Calendar || value is Long) {
            val formatString = options
                .appendPrintfOptions(StringBuilder("%"))
                .append(if (options.shouldUpperCase()) 'T' else 't')
                .append(format.char)
                .toString()
            out.append(String.format(FORMAT_LOCALE, formatString, value))
        } else {
            appendInvalid(out, value, "%t" + format.char)
        }
    }

    override fun visitPreformatted(value: Any?, formatted: String) {
        // For unstructured logging we just use the pre-formatted string.
        out.append(formatted)
    }

    override fun visitMissing() {
        out.append(MISSING_ARGUMENT_MESSAGE)
    }

    override fun visitNull() {
        out.append("null")
    }

    public companion object {

        /**
         * Literal string to be inlined whenever a placeholder references a non-existent argument.
         */
        private const val MISSING_ARGUMENT_MESSAGE = "[ERROR: MISSING LOG ARGUMENT]"

        /**
         * Literal string to be appended wherever additional unused arguments are provided.
         */
        private const val EXTRA_ARGUMENT_MESSAGE = " [ERROR: UNUSED LOG ARGUMENTS]"

        /**
         * The number of bits in `char` type.
         */
        private const val BITS_IN_CHAR = 16

        /**
         * Appends the formatted log message of the given log data to the given buffer.
         *
         * Note that the [LogData] need not have a template context or arguments, it might just
         * have a literal argument, which will be appended without additional formatting.
         *
         * @param data The log data with the message to be appended.
         * @param out A buffer to append to.
         * @return The given buffer (for method chaining).
         */
        @JvmStatic
        @CanIgnoreReturnValue
        public fun appendFormattedMessage(data: LogData, out: StringBuilder): StringBuilder {
            if (data.templateContext != null) {
                val formatter = BaseMessageFormatter(data.templateContext!!, data.arguments, out)
                val result = formatter.build()
                if (data.arguments.size > formatter.expectedArgumentCount) {
                    // TODO(dbeaumont): Do better and look at adding formatted values or maybe just a count?
                    result.append(EXTRA_ARGUMENT_MESSAGE)
                }
                return result
            } else {
                out.append(safeToString(data.literalArgument))
            }
            return out
        }

        private fun appendFormatted(
            out: StringBuilder,
            value: Any?,
            format: FormatChar,
            options: FormatOptions
        ) {
            if (handleCommonCases(out, value, format, options)) {
                return
            }
            // Default handle for rare cases that need non-trivial formatting.
            var formatString = format.defaultFormatString
            if (!options.isDefault) {
                var chr = format.char
                if (options.shouldUpperCase()) {
                    // Clear 6th bit to convert lower case ASCII to upper case.
                    chr = chr.uppercaseChar()
                }
                formatString = options.appendPrintfOptions(StringBuilder("%"))
                    .append(chr)
                    .toString()
            }
            out.append(String.format(FORMAT_LOCALE, formatString, value))
        }

        private fun handleCommonCases(
            out: StringBuilder,
            value: Any?,
            format: FormatChar,
            options: FormatOptions
        ): Boolean {
            return when (format) {
                STRING -> handleString(out, value, options)
                DECIMAL, BOOLEAN -> {
                    handleDecimalOrBoolean(out, value, options)
                }
                HEX -> handleHex(out, value, options)
                CHAR -> handleChar(out, value, options)
                else -> false
            }
        }

        private fun handleString(out: StringBuilder, value: Any?, options: FormatOptions): Boolean {
            var handled = false
            if (value !is Formattable) {
                if (options.isDefault) {
                    out.append(safeToString(value))
                    handled = true
                }
            } else {
                MessageUtils.safeFormatTo(value, out, options)
                handled = true
            }
            return handled
        }

        private fun handleDecimalOrBoolean(
            out: StringBuilder, 
            value: Any?, 
            options: FormatOptions
        ): Boolean {
            if (options.isDefault) {
                out.append(value)
                return true
            }
            return false
        }

        private fun handleHex(out: StringBuilder, value: Any?, options: FormatOptions): Boolean {
            if (options.filter(FLAG_UPPER_CASE, false, false) == options) {
                appendHex(out, value as Number, options)
                return true
            }
            return false
        }

        private fun handleChar(
            out: StringBuilder, 
            value: Any?, 
            options: FormatOptions
        ): Boolean {
            if (options.isDefault) {
                if (value is Char) {
                    out.append(value)
                } else {
                    val codePoint = (value as Number).toInt()
                    if (codePoint ushr BITS_IN_CHAR == 0) {
                        out.append(codePoint.toChar())
                    } else {
                        out.append(Character.toChars(codePoint))
                    }
                }
                return true
            }
            return false
        }

        private fun appendInvalid(out: StringBuilder, value: Any?, formatString: String) {
            out.append("[INVALID: format=")
                .append(formatString)
                .append(", type=")
                .append(value?.javaClass?.canonicalName)
                .append(", value=")
                .append(safeToString(value))
                .append(']')
        }
    }
}
