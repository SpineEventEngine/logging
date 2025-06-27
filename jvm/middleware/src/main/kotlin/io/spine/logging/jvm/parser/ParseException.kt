package io.spine.logging.jvm.parser

import java.io.Serial

/**
 * The exception that should be thrown whenever parsing of a log message fails.
 * This exception must not be thrown outside of template parsing.
 */
class ParseException private constructor(errorMessage: String) : RuntimeException(errorMessage) {

    companion object {
        /** The prefix/suffix to show when an error snippet is truncated. */
        private const val ELLIPSIS = "..."

        /** The length of the snippet to show before and after the error. */
        private const val SNIPPET_LENGTH = 5

        @Serial
        @JvmField
        val serialVersionUID: Long = 0L

        @JvmStatic
        fun withBounds(errorMessage: String, logMessage: String, start: Int, end: Int): ParseException =
            ParseException(msg(errorMessage, logMessage, start, end))

        @JvmStatic
        fun atPosition(errorMessage: String, logMessage: String, position: Int): ParseException =
            ParseException(msg(errorMessage, logMessage, position, position + 1))

        @JvmStatic
        fun withStartPosition(errorMessage: String, logMessage: String, start: Int): ParseException =
            ParseException(msg(errorMessage, logMessage, start, -1))

        @JvmStatic
        internal fun generic(errorMessage: String): ParseException = ParseException(errorMessage)

        private fun msg(errorMessage: String, logMessage: String, errorStart: Int, errorEndOriginal: Int): String {
            var errorEnd = errorEndOriginal
            if (errorEnd < 0) {
                errorEnd = logMessage.length
            }
            val out = StringBuilder(errorMessage).append(": ")
            if (errorStart > SNIPPET_LENGTH + ELLIPSIS.length) {
                out.append(ELLIPSIS).append(logMessage, errorStart - SNIPPET_LENGTH, errorStart)
            } else {
                out.append(logMessage, 0, errorStart)
            }
            out.append('[').append(logMessage.substring(errorStart, errorEnd)).append(']')
            if (logMessage.length - errorEnd > SNIPPET_LENGTH + ELLIPSIS.length) {
                out.append(logMessage, errorEnd, errorEnd + SNIPPET_LENGTH).append(ELLIPSIS)
            } else {
                out.append(logMessage, errorEnd, logMessage.length)
            }
            return out.toString()
        }
    }

    override fun fillInStackTrace(): Throwable = this
}
