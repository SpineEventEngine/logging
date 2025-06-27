package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.parameter.DateTimeFormat
import io.spine.logging.jvm.parameter.DateTimeParameter
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.parameter.ParameterVisitor
import io.spine.logging.jvm.parameter.SimpleParameter

/**
 * Default implementation of the printf message parser. This parser supports all the
 * place-holders available in `String.format` but can be extended for additional behaviour.
 * For consistency it is recommended, but not required, that custom printf parsers extend from this class.
 */
class DefaultPrintfMessageParser private constructor() : PrintfMessageParser() {

    override fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int {
        var termEnd = formatStart + 1
        val typeChar = message[formatStart]
        val isUpperCase = typeChar.code and 0x20 == 0
        val options = FormatOptions.parse(message, specStart, formatStart, isUpperCase)

        val parameter: Parameter = when (val formatChar = FormatChar.of(typeChar)) {
            null -> when (typeChar) {
                't', 'T' -> {
                    if (!options.validate(FormatOptions.FLAG_LEFT_ALIGN or FormatOptions.FLAG_UPPER_CASE, false)) {
                        throw ParseException.withBounds("invalid format specification", message, termStart, termEnd)
                    }
                    termEnd += 1
                    if (termEnd > message.length) {
                        throw ParseException.atPosition("truncated format specifier", message, termStart)
                    }
                    val dateTimeFormat = DateTimeFormat.of(message[formatStart + 1])
                        ?: throw ParseException.atPosition("illegal date/time conversion", message, formatStart + 1)
                    DateTimeParameter.of(dateTimeFormat, options, index)
                }
                'h', 'H' -> {
                    if (!options.validate(FormatOptions.FLAG_LEFT_ALIGN or FormatOptions.FLAG_UPPER_CASE, false)) {
                        throw ParseException.withBounds("invalid format specification", message, termStart, termEnd)
                    }
                    wrapHexParameter(options, index)
                }
                else -> {
                    throw ParseException.withBounds("invalid format specification", message, termStart, formatStart + 1)
                }
            }
            else -> {
                if (!options.areValidFor(formatChar)) {
                    throw ParseException.withBounds("invalid format specifier", message, termStart, termEnd)
                }
                SimpleParameter.of(index, formatChar, options)
            }
        }
        builder.addParameter(termStart, termEnd, parameter)
        return termEnd
    }

    companion object {
        private val INSTANCE: PrintfMessageParser = DefaultPrintfMessageParser()

        @JvmStatic
        fun getInstance(): PrintfMessageParser = INSTANCE

        // Static method so the anonymous synthetic parameter is static, rather than an inner class.
        private fun wrapHexParameter(options: FormatOptions, index: Int): Parameter {
            return object : Parameter(options, index) {
                override fun accept(visitor: ParameterVisitor, value: Any) {
                    visitor.visit(value.hashCode(), FormatChar.HEX, formatOptions)
                }

                override fun getFormat(): String = if (options.shouldUpperCase()) "%H" else "%h"
            }
        }
    }
}
