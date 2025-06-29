package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions

/** Visitor of log message arguments. */
interface ParameterVisitor {
    fun visit(value: Any, format: FormatChar, options: FormatOptions)

    fun visitDateTime(value: Any, format: DateTimeFormat, options: FormatOptions)

    fun visitPreformatted(value: Any, formatted: String)

    fun visitMissing()

    fun visitNull()
}
