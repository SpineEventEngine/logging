package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatOptions

/** Parameter for formatting date/time arguments. */
class DateTimeParameter private constructor(
    options: FormatOptions,
    index: Int,
    private val format: DateTimeFormat
) : Parameter(options, index) {

    private val formatString: String = buildString {
        append('%')
        formatOptions.appendPrintfOptions(this)
        append(if (formatOptions.shouldUpperCase()) 'T' else 't')
        append(format.char)
    }

    override fun accept(visitor: ParameterVisitor, value: Any) {
        visitor.visitDateTime(value, format, formatOptions)
    }

    override fun getFormat(): String = formatString

    companion object {
        @JvmStatic
        fun of(format: DateTimeFormat, options: FormatOptions, index: Int): Parameter =
            DateTimeParameter(options, index, format)
    }
}
