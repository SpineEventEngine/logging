package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.backend.FormatType
import java.text.MessageFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Parameter implementation for brace style placeholders `{n}`. */
class BraceStyleParameter private constructor(index: Int) : Parameter(FormatOptions.getDefault(), index) {

    override fun accept(visitor: ParameterVisitor, value: Any) {
        when {
            FormatType.INTEGRAL.canFormat(value) ->
                visitor.visit(value, FormatChar.DECIMAL, WITH_GROUPING)
            FormatType.FLOAT.canFormat(value) ->
                visitor.visit(value, FormatChar.FLOAT, WITH_GROUPING)
            value is Date -> {
                val formatted = (prototypeMessageFormatter.clone() as MessageFormat)
                    .format(arrayOf(value), StringBuffer(), null)
                    .toString()
                visitor.visitPreformatted(value, formatted)
            }
            value is Calendar ->
                visitor.visitDateTime(value, DateTimeFormat.DATETIME_FULL, formatOptions)
            else ->
                visitor.visit(value, FormatChar.STRING, formatOptions)
        }
    }

    override fun getFormat(): String = "%s"

    companion object {
        private const val MAX_CACHED_PARAMETERS = 10
        private val WITH_GROUPING = FormatOptions.of(
            FormatOptions.FLAG_SHOW_GROUPING,
            FormatOptions.UNSET,
            FormatOptions.UNSET
        )
        private val prototypeMessageFormatter = MessageFormat("{0}", Locale.ROOT)
        private val DEFAULT_PARAMETERS = Array(MAX_CACHED_PARAMETERS) { BraceStyleParameter(it) }

        @JvmStatic
        fun of(index: Int): BraceStyleParameter =
            if (index < MAX_CACHED_PARAMETERS) DEFAULT_PARAMETERS[index] else BraceStyleParameter(index)
    }
}
