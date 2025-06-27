package io.spine.logging.jvm.parser

/**
 * A specialized [MessageParser] for processing log messages in printf style, as used by [String.format].
 * This is an abstract parser which knows how to process and extract placeholder terms at a high level,
 * but does not impose its own semantics for place-holder types.
 */
abstract class PrintfMessageParser : MessageParser() {

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapePrintf(out, message, start, end)
    }

    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.message
        var lastResolvedIndex = -1
        var implicitIndex = 0
        var pos = nextPrintfTerm(message, 0)
        while (pos >= 0) {
            val termStart = pos++
            var optionsStart = pos
            var c: Char
            var index = 0
            while (true) {
                if (pos < message.length) {
                    c = message[pos++]
                    val digit = c - '0'
                    if (digit in 0..9) {
                        index = 10 * index + digit
                        if (index < MAX_ARG_COUNT) continue
                        throw ParseException.withBounds("index too large", message, termStart, pos)
                    }
                    break
                }
                throw ParseException.withStartPosition("unterminated parameter", message, termStart)
            }
            if (c == '$') {
                val indexLen = (pos - 1) - optionsStart
                if (indexLen == 0) {
                    throw ParseException.withBounds("missing index", message, termStart, pos)
                }
                if (message[optionsStart] == '0') {
                    throw ParseException.withBounds("index has leading zero", message, termStart, pos)
                }
                index -= 1
                optionsStart = pos
                if (pos == message.length) {
                    throw ParseException.withStartPosition("unterminated parameter", message, termStart)
                }
                c = message[pos++]
            } else if (c == '<') {
                if (lastResolvedIndex == -1) {
                    throw ParseException.withBounds("invalid relative parameter", message, termStart, pos)
                }
                index = lastResolvedIndex
                optionsStart = pos
                if (pos == message.length) {
                    throw ParseException.withStartPosition("unterminated parameter", message, termStart)
                }
                c = message[pos++]
            } else {
                index = implicitIndex++
            }
            pos = findFormatChar(message, termStart, pos - 1)
            pos = parsePrintfTerm(builder, index, message, termStart, optionsStart, pos)
            lastResolvedIndex = index
        }
    }

    /**
     * Parses a single printf-like term from a log message into a message template builder.
     */
    @Throws(ParseException::class)
    abstract fun parsePrintfTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        specStart: Int,
        formatStart: Int
    ): Int

    companion object {
        private const val ALLOWED_NEWLINE_PATTERN = "\n|\r(?:\n)?"
        private val SYSTEM_NEWLINE: String = getSafeSystemNewline()

        /**
         * Returns the system newline separator avoiding any issues with security exceptions or
         * suspicious values. The only allowed return values are "\n" (default), "\r" or "\r\n".
         */
        @JvmStatic
        fun getSafeSystemNewline(): String {
            return try {
                val unsafeNewline = System.getProperty("line.separator")
                if (unsafeNewline.matches(ALLOWED_NEWLINE_PATTERN.toRegex())) unsafeNewline else "\n"
            } catch (e: SecurityException) {
                "\n"
            }
        }

        /** Returns the index of the first unescaped '%' character in [message] starting at [pos] (or -1 if not found). */
        @JvmStatic
        internal fun nextPrintfTerm(message: String, pos: Int): Int {
            var p = pos
            while (p < message.length) {
                if (message[p++] != '%') {
                    continue
                }
                if (p < message.length) {
                    val c = message[p]
                    if (c == '%' || c == 'n') {
                        p += 1
                        continue
                    }
                    return p - 1
                }
                throw ParseException.withStartPosition("trailing unquoted '%' character", message, p - 1)
            }
            return -1
        }

        private fun findFormatChar(message: String, termStart: Int, pos: Int): Int {
            var p = pos
            while (p < message.length) {
                val c = message[p]
                val alpha = (c.code and 0xDF) - 'A'.code
                if (alpha in 0..25) {
                    return p
                }
                p++
            }
            throw ParseException.withStartPosition("unterminated parameter", message, termStart)
        }

        /** Unescapes the characters in the sub-string according to printf style formatting rules. */
        @JvmStatic
        internal fun unescapePrintf(out: StringBuilder, message: String, start: Int, end: Int) {
            var pos = start
            var s = start
            while (pos < end) {
                if (message[pos++] != '%') {
                    continue
                }
                if (pos == end) {
                    break
                }
                val chr = message[pos]
                if (chr == '%') {
                    out.append(message, s, pos)
                } else if (chr == 'n') {
                    out.append(message, s, pos - 1)
                    out.append(SYSTEM_NEWLINE)
                } else {
                    continue
                }
                s = ++pos
            }
            if (s < end) {
                out.append(message, s, end)
            }
        }
    }
}
