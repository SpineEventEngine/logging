package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatChar
import io.spine.logging.jvm.backend.FormatOptions

/**
 * Visitor of log message arguments.
 */
public interface ParameterVisitor {

    public fun visit(value: Any, format: FormatChar, options: FormatOptions)

    public fun visitDateTime(value: Any, format: DateTimeFormat, options: FormatOptions)

    public fun visitPreformatted(value: Any, formatted: String)

    public fun visitMissing()

    public fun visitNull()
}
