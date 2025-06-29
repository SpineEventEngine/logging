package io.spine.logging.jvm.parameter

import io.spine.logging.jvm.backend.FormatOptions

/**
 * An abstract representation of a parameter for a message template.
 * All subclasses must be immutable and thread safe.
 */
abstract class Parameter protected constructor(
    options: FormatOptions?,
    index: Int
) {

    val index: Int
    protected val formatOptions: FormatOptions

    init {
        requireNotNull(options) { "format options cannot be null" }
        require(index >= 0) { "invalid index: $index" }
        this.index = index
        this.formatOptions = options
    }

    fun accept(visitor: ParameterVisitor, args: Array<out Any?>) {
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

    abstract fun getFormat(): String
}
