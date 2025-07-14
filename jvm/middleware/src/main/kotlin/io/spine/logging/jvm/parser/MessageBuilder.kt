/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.logging.jvm.parser

import io.spine.logging.jvm.backend.TemplateContext
import io.spine.logging.jvm.parameter.Parameter
import io.spine.logging.jvm.util.Checks

/**
 * A builder which is used during message parsing to create a message object which
 * encapsulates all the formatting requirements of a log message.
 *
 * One message builder is created for each log message that's parsed.
 *
 * @param T The message type being built.
 *
 * @see [Original Java code of Google Flogger](https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/MessageBuilder.java)
 * for historical context.
 */
public abstract class MessageBuilder<T>(context: TemplateContext) {

    private val context: TemplateContext = Checks.checkNotNull(context, "context")

    /**
     *  Mask of parameter indexes seen during parsing, used to determine if there are gaps in the
     *  specified parameters (which is a parsing error).
     *
     * This could be a long if we cared about tracking up to 64 parameters, but I suspect we don't.
     */
    private var pmask: Int = 0

    /**
     * The maximum argument index referenced by the formatted message (only valid after parsing).
     */
    private var maxIndex: Int = -1

    /**
     * Returns the parser used to process the log format message in this builder.
     */
    public val parser: MessageParser
        get() = context.parser

    /**
     * Returns the log format message to be parsed by this builder.
     */
    public val message: String
        get() = context.message

    /**
     * Returns the expected number of arguments to be formatted by this message.
     *
     * This is only valid once parsing has completed successfully.
     */
    public val expectedArgumentCount: Int
        get() = maxIndex + 1


    /**
     * Called by parser implementations to signify that the parsing of the next
     * parameter is complete.
     * 
     * This method will call [addParameterImpl] with exactly the same
     * arguments, but may also do additional work before or after that call.
     *
     * @param termStart The index of the first character in the log message string that
     *        was parsed to form the given parameter.
     * @param termEnd The index after the last character in the log message string that
     *        was parsed to form the given parameter.
     * @param param A parameter representing the format specified by the substring of
     *        the log message in the range `[termStart, termEnd)`.
     */
    @Suppress("MagicNumber")
    public fun addParameter(termStart: Int, termEnd: Int, param: Parameter) {
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
     *
     * Note that each successive call to this method during parsing will specify a disjoint ranges of
     * characters from the log message and that each range will be higher that the previously
     * specified one.
     *
     * @param termStart The index of the first character in the log message string that was parsed to
     *        form the given parameter.
     * @param termEnd The index after the last character in the log message string that was parsed to
     *        form the given parameter.
     * @param param A parameter representing the format specified by the substring of the log message
     *        in the range `[termStart, termEnd)`.
     */
    protected abstract fun addParameterImpl(termStart: Int, termEnd: Int, param: Parameter)

    /**
     * Returns the implementation-specific result of parsing the current log message.
     */
    protected abstract fun buildImpl(): T

    /**
     * Builds a log message using the current message context.
     *
     * @return the implementation-specific result of parsing the current log message.
     */
    @Suppress("UNCHECKED_CAST", "ImplicitDefaultLocale", "MagicNumber")
    public fun build(): T {
        // We need to use reflection to call the protected parseImpl method.
        val parseImplMethod =
            MessageParser::class.java.getDeclaredMethod("parseImpl", MessageBuilder::class.java)
        parseImplMethod.isAccessible = true
        parseImplMethod.invoke(parser, this)
        
        // There was a gap in the parameters if either:
        // 1) the mask had a gap, e.g., `..00110111`
        // 2) there were more than 32 parameters, and the mask was not full.
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
