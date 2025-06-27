package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.TemplateContext
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.util.Checks

/**
 * A builder which is used during message parsing to create a message object which encapsulates
 * all the formatting requirements of a log message. One message builder is created for each log
 * message that's parsed.
 */
abstract class MessageBuilder<T>(private val context: TemplateContext) {

    // Mask of parameter indices seen during parsing, used to determine if there are gaps in
    // the specified parameters (which is a parsing error).
    // This could be a long if we cared about tracking up to 64 parameters, but I suspect we don't.
    private var pmask: Int = 0

    // The maximum argument index referenced by the formatted message (only valid after parsing).
    private var maxIndex: Int = -1

    init {
        Checks.checkNotNull(context, "context")
    }

    /** Returns the parser used to process the log format message in this builder. */
    fun getParser(): MessageParser = context.parser

    /** Returns the log format message to be parsed by this builder. */
    fun getMessage(): String = context.message

    /**
     * Returns the expected number of arguments to be formatted by this message. This is only valid
     * once parsing has completed successfully.
     */
    fun getExpectedArgumentCount(): Int = maxIndex + 1

    /**
     * Called by parser implementations to signify that the parsing of the next parameter is complete.
     * This method will call [addParameterImpl] with exactly the same arguments, but may also do
     * additional work before or after that call.
     */
    fun addParameter(termStart: Int, termEnd: Int, param: Parameter) {
        // Set a bit in the parameter mask according to which parameter was referenced.
        // Shifting wraps, so we must do a check here.
        if (param.index < 32) {
            pmask = pmask or (1 shl param.index)
        }
        maxIndex = maxOf(maxIndex, param.index)
        addParameterImpl(termStart, termEnd, param)
    }

    /**
     * Adds the specified parameter to the format instance currently being built. This method is to
     * signify that the parsing of the next parameter is complete.
     */
    protected abstract fun addParameterImpl(termStart: Int, termEnd: Int, param: Parameter)

    /** Returns the implementation specific result of parsing the current log message. */
    protected abstract fun buildImpl(): T

    /**
     * Builds a log message using the current message context.
     *
     * @return the implementation specific result of parsing the current log message.
     */
    fun build(): T {
        getParser().parseImpl(this)
        // There was a gap in the parameters if either:
        // 1) the mask had a gap, e.g., ..00110111
        // 2) there were more than 32 parameters and the mask wasn't full.
        // Gaps above the 32nd parameter are not detected.
        if ((pmask and (pmask + 1)) != 0 || (maxIndex > 31 && pmask != -1)) {
            val firstMissing = Integer.numberOfTrailingZeros(pmask.inv())
            throw ParseException.generic(
                String.format("unreferenced arguments [first missing index=%d]", firstMissing)
            )
        }
        return buildImpl()
    }
}
