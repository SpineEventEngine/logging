package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions

/** Parameter which formats arguments according to [FormatChar]. */
class SimpleParameter private constructor(
    index: Int,
    private val formatChar: FormatChar,
    options: FormatOptions
) : Parameter(options, index) {

    private val formatString: String = if (formatOptions.isDefault) {
        formatChar.defaultFormatString
    } else buildFormatString(formatOptions, formatChar)

    override fun accept(visitor: ParameterVisitor, value: Any) {
        visitor.visit(value, formatChar, formatOptions)
    }

    override fun getFormat(): String = formatString

    companion object {
        private const val MAX_CACHED_PARAMETERS = 10
        private val DEFAULT_PARAMETERS: Map<FormatChar, Array<SimpleParameter>>

        init {
            val map = mutableMapOf<FormatChar, Array<SimpleParameter>>()
            for (fc in FormatChar.values()) {
                map[fc] = createParameterArray(fc)
            }
            DEFAULT_PARAMETERS = map
        }

        private fun createParameterArray(formatChar: FormatChar): Array<SimpleParameter> =
            Array(MAX_CACHED_PARAMETERS) { SimpleParameter(it, formatChar, FormatOptions.getDefault()) }

        @JvmStatic
        fun of(index: Int, formatChar: FormatChar, options: FormatOptions): SimpleParameter {
            return if (index < MAX_CACHED_PARAMETERS && options.isDefault) {
                DEFAULT_PARAMETERS[formatChar]!![index]
            } else {
                SimpleParameter(index, formatChar, options)
            }
        }

        @JvmStatic
        internal fun buildFormatString(options: FormatOptions, formatChar: FormatChar): String {
            var c = formatChar.char
            if (options.shouldUpperCase()) {
                c = (c.code and 0xDF).toChar()
            }
            return options.appendPrintfOptions(StringBuilder("%")).append(c).toString()
        }
    }
}
