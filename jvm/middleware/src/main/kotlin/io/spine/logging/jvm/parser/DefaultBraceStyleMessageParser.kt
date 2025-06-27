package io.spine.logging.jvm.parser

import io.spine.logging.jvm.parameter.BraceStyleParameter

/**
 * Default implementation of the brace style message parser. Note that while the
 * underlying parsing mechanism supports the more general "{n,xxx}" form for brace
 * format style logging, the default message parser is currently limited to
 * simple indexed place holders (e.g. "{0}"). This class could easily be extended
 * to support these trailing format specifiers.
 */
class DefaultBraceStyleMessageParser private constructor() : BraceStyleMessageParser() {

    override fun parseBraceFormatTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        formatStart: Int,
        termEnd: Int
    ) {
        if (formatStart != -1) {
            throw ParseException.withBounds(
                "the default brace style parser does not allow trailing format specifiers",
                message,
                formatStart - 1,
                termEnd - 1
            )
        }
        builder.addParameter(termStart, termEnd, BraceStyleParameter.of(index))
    }

    companion object {
        private val INSTANCE: BraceStyleMessageParser = DefaultBraceStyleMessageParser()

        @JvmStatic
        fun getInstance(): BraceStyleMessageParser = INSTANCE
    }
}
