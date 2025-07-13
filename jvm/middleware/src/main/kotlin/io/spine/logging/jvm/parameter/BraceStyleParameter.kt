package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions
import io.spine.logging.jvm.backend.FormatType
import java.text.MessageFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Parameter implementation for brace style placeholders `{n}`.
 */
public class BraceStyleParameter private constructor(index: Int) :
    Parameter(FormatOptions.getDefault(), index) {

    override fun accept(visitor: ParameterVisitor, value: Any) {
        when {
            FormatType.INTEGRAL.canFormat(value) ->
                visitor.visit(value, FormatChar.DECIMAL, withGroupings)
            FormatType.FLOAT.canFormat(value) ->
                visitor.visit(value, FormatChar.FLOAT, withGroupings)
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

    public companion object {

        private const val MAX_CACHED_PARAMETERS = 10
        private val defaultParameters = Array(MAX_CACHED_PARAMETERS) { BraceStyleParameter(it) }

        private val withGroupings = FormatOptions.of(
            FormatOptions.FLAG_SHOW_GROUPING,
            FormatOptions.UNSET,
            FormatOptions.UNSET
        )
        private val prototypeMessageFormatter = MessageFormat("{0}", Locale.ROOT)

        @JvmStatic
        public fun of(index: Int): BraceStyleParameter =
            if (index < MAX_CACHED_PARAMETERS) {
                defaultParameters[index]
            } else {
                BraceStyleParameter(index)
            }
    }
}
