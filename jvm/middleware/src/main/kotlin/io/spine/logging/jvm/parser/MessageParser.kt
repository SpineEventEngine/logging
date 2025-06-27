package io.spine.logging.jvm.parser

/**
 * Base class from which any specific message parsers are derived
 * (e.g. [PrintfMessageParser] and [BraceStyleMessageParser]).
 */
abstract class MessageParser {

    companion object {
        /**
         * The maximum allowed index (this should correspond to the MAX_ALLOWED_WIDTH
         * in [io.spine.logging.jvm.backend.FormatOptions] because at times it is
         * ambiguous as to which is being parsed).
         */
        const val MAX_ARG_COUNT: Int = 1_000_000
    }

    /**
     * Abstract parse method implemented by specific subclasses to modify parsing behaviour.
     *
     * Implementations of this method are required to invoke
     * [MessageBuilder.addParameterImpl] once for each parameter place-holder in the message.
     */
    @Throws(ParseException::class)
    protected abstract fun <T> parseImpl(builder: MessageBuilder<T>)

    /**
     * Appends the unescaped literal representation of the given message string
     * (assumed to be escaped according to this parser's escaping rules).
     */
    abstract fun unescape(out: StringBuilder, message: String, start: Int, end: Int)
}
