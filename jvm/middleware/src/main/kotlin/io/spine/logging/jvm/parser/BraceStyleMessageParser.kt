package io.spine.logging.jvm.parser

/**
 * A specialized [MessageParser] for processing log messages in the "brace style",
 * as used by [java.text.MessageFormat].
 * This is an abstract parser which knows how to process and extract place-holder
 * terms at a high level, but does not impose its own semantics on formatting extensions.
 */
abstract class BraceStyleMessageParser : MessageParser() {

    /**
     * Parses a single brace format term from a log message into a message template builder.
     */
    @Throws(ParseException::class)
    abstract fun parseBraceFormatTerm(
        builder: MessageBuilder<*>,
        index: Int,
        message: String,
        termStart: Int,
        formatStart: Int,
        termEnd: Int
    )

    override fun unescape(out: StringBuilder, message: String, start: Int, end: Int) {
        unescapeBraceFormat(out, message, start, end)
    }

    override fun <T> parseImpl(builder: MessageBuilder<T>) {
        val message = builder.message
        var pos = nextBraceFormatTerm(message, 0)
        while (pos >= 0) {
            val termStart = pos++
            val indexStart = termStart + 1
            var c: Char
            var index = 0
            while (true) {
                if (pos < message.length) {
                    c = message[pos++]
                    val digit = c - '0'
                    if (digit in 0..9) {
                        index = 10 * index + digit
                        if (index < MAX_ARG_COUNT) continue
                        throw ParseException.withBounds("index too large", message, indexStart, pos)
                    }
                    break
                }
                throw ParseException.withStartPosition("unterminated parameter", message, termStart)
            }
            val indexLen = (pos - 1) - indexStart
            if (indexLen == 0) {
                throw ParseException.withBounds("missing index", message, termStart, pos)
            }
            if (message[indexStart] == '0' && indexLen > 1) {
                throw ParseException.withBounds("index has leading zero", message, indexStart, pos - 1)
            }
            val trailingPartStart: Int
            if (c == '}') {
                trailingPartStart = -1
            } else if (c == BRACE_STYLE_SEPARATOR) {
                trailingPartStart = pos
                do {
                    if (pos == message.length) {
                        throw ParseException.withStartPosition("unterminated parameter", message, termStart)
                    }
                } while (message[pos++] != '}')
            } else {
                throw ParseException.withBounds("malformed index", message, termStart + 1, pos)
            }
            parseBraceFormatTerm(builder, index, message, termStart, trailingPartStart, pos)
            pos = nextBraceFormatTerm(message, pos)
        }
    }

    companion object {
        private const val BRACE_STYLE_SEPARATOR = ','

        /** Returns the index of the next unquoted '{' character in [message] starting at [pos] (or -1 if not found). */
        @JvmStatic
        internal fun nextBraceFormatTerm(message: String, pos: Int): Int {
            var p = pos
            while (p < message.length) {
                val c = message[p++]
                if (c == '{') {
                    return p - 1
                }
                if (c != '\'') {
                    continue
                }
                if (p == message.length) {
                    throw ParseException.withStartPosition("trailing single quote", message, p - 1)
                }
                if (message[p++] == '\'') {
                    continue
                }
                val quote = p - 2
                do {
                    if (p == message.length) {
                        throw ParseException.withStartPosition("unmatched single quote", message, quote)
                    }
                } while (message[p++] != '\'')
            }
            return -1
        }

        /** Unescapes the characters in the substring according to brace formatting rules. */
        @JvmStatic
        internal fun unescapeBraceFormat(out: StringBuilder, message: String, start: Int, end: Int) {
            var pos = start
            var isQuoted = false
            var s = start
            while (pos < end) {
                var c = message[pos++]
                if (c != '\\' && c != '\'') {
                    continue
                }
                val quoteStart = pos - 1
                if (c == '\\') {
                    c = message[pos++]
                    if (c != '\'') {
                        continue
                    }
                }
                out.append(message, s, quoteStart)
                s = pos
                if (pos == end) break
                if (isQuoted) {
                    isQuoted = false
                } else if (message[pos] != '\'') {
                    isQuoted = true
                } else {
                    pos++
                }
            }
            if (s < end) {
                out.append(message, s, end)
            }
        }
    }
}
