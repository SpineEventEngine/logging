package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatOptions

/**
 * An abstract representation of a parameter for a message template.
 *
 * All subclasses must be immutable and thread-safe.
 */
public abstract class Parameter protected constructor(
    public val formatOptions: FormatOptions,
    public val index: Int
) {
    init {
        require(index >= 0) { "Invalid index: $index" }
    }

    public fun accept(visitor: ParameterVisitor, args: Array<out Any?>) {
        if (index < args.size) {
            val value = args[index]
            if (value != null) {
                accept(visitor, value)
            } else {
                visitor.visitNull()
            }
        } else {
            visitor.visitMissing()
        }
    }

    protected abstract fun accept(visitor: ParameterVisitor, value: Any)

    public abstract fun getFormat(): String
}
