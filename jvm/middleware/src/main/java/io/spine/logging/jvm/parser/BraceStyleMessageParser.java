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

package io.spine.logging.jvm.parser;

/**
 * A specialized {@link MessageParser} for processing log messages in the "brace style", as used by
 * {@link java.text.MessageFormat MessageFormat}. This is an abstract parser which knows how to
 * process and extract place-holder terms at a high level, but does not impose its own semantics
 * on formatting extensions (eg, "{0,number,#.##}").
 *
 * <p>
 * Typically you should not subclass this class, but instead subclass
 * {@link DefaultBraceStyleMessageParser}, which provides default behavior for simple place-holders.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/BraceStyleMessageParser.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public abstract class BraceStyleMessageParser extends MessageParser {
  /**
   * The character used to delimit the argument index from the trailing part in brace style
   * formatting.
   */
  private static final char BRACE_STYLE_SEPARATOR = ',';

  /**
   * Parses a single brace format term from a log message into a message template builder. Note that
   * the default brace style parser currently does not handle anything other than the simplest "{n}"
   * forms of parameter specification, and it will treat anything more complex as a parsing error.
 *
   * <p>
   * A simple example of a positional parameter:
   * <pre>
   * message: "Hello {0} World"
   * termStart: 6 ───┚  ╿
   * formatStart: -1    │
   * termEnd: 9 ────────╯
   * </pre>
 *
   * <p>
   * A more complex example with a trailing format specification:
   * <pre>
   * message: "Hello {0,number,#} World"
   * termStart: 6 ───┚  ╿        ╿
   * formatStart: 9 ────╯        │
   * termEnd: 18 ────────────────╯
   * </pre>
   *
   * @param builder the message template builder.
   * @param index the zero-based argument index for the parameter.
   * @param message the complete log message string.
   * @param termStart the index of the initial '{' character that starts the term.
   * @param formatStart the index of the optional formatting substring after the first comma
   *        (which extends to {@code termEnd - 1}) or -1 if there is no formatting substring.
 *
   * @param termEnd the index after the final '}' character that completes this term.
   */
  abstract void parseBraceFormatTerm(
      MessageBuilder<?> builder,
      int index,
      String message,
      int termStart,
      int formatStart,
      int termEnd)
      throws ParseException;

  @Override
  public final void unescape(StringBuilder out, String message, int start, int end) {
    unescapeBraceFormat(out, message, start, end);
  }

  @Override
  protected final <T> void parseImpl(MessageBuilder<T> builder) throws ParseException {
    String message = builder.getMessage();
    for (int pos = nextBraceFormatTerm(message, 0);
        pos >= 0;
        pos = nextBraceFormatTerm(message, pos)) {
      // Capture the term start and move on (the character here is always '%').
      int termStart = pos++;
      // For brace format strings we know there must be an index and it starts just after the '{'.
      int indexStart = termStart + 1;

      // STEP 1: Parse the numeric value at the start of the term.
      char c;
      int index = 0;
      while (true) {
        if (pos < message.length()) {
          // Casting to char makes the result unsigned, so we don't need to test "digit < 0" later.
          c = message.charAt(pos++);
          int digit = (char) (c - '0');
          if (digit < 10) {
            index = (10 * index) + digit;
            if (index < MAX_ARG_COUNT) {
              continue;
            }
            throw ParseException.withBounds("index too large", message, indexStart, pos);
          }
          break;
        }
        throw ParseException.withStartPosition("unterminated parameter", message, termStart);
      }

      // Note that we could have got here without parsing any digits.
      int indexLen = (pos - 1) - indexStart;
      if (indexLen == 0) {
        // We might want to support "{}" as the implicit placeholder one day.
        throw ParseException.withBounds("missing index", message, termStart, pos);
      }
      // Indices are zero based so we can have a leading zero, but only if it's the only digit.
      if (message.charAt(indexStart) == '0' && indexLen > 1) {
        throw ParseException.withBounds("index has leading zero", message, indexStart, pos - 1);
      }

      // STEP 2: Determine it there's a trailing part to the term.
      int trailingPartStart;
      if (c == '}') {
        // Well formatted without a separator: "{nn}"
        trailingPartStart = -1;
      } else if (c == BRACE_STYLE_SEPARATOR) {
        trailingPartStart = pos;
        do {
          if (pos == message.length()) {
            throw ParseException.withStartPosition("unterminated parameter", message, termStart);
          }
        } while (message.charAt(pos++) != '}');
        // Well formatted with trailing part.
      } else {
        throw ParseException.withBounds("malformed index", message, termStart + 1, pos);
      }

      // STEP 3: Invoke the term parsing method.
      parseBraceFormatTerm(builder, index, message, termStart, trailingPartStart, pos);
    }
  }

  /**
   * Returns the index of the next unquoted '{' character in message starting at pos (or -1 if not
   * found).
   */
  // VisibleForTesting
  static int nextBraceFormatTerm(String message, int pos) throws ParseException {
    // We can assume that we start in unquoted mode.
    while (pos < message.length()) {
      char c = message.charAt(pos++);
      if (c == '{') {
        // We found an unquoted open bracket. Hurrah!
        return pos - 1;
      }
      if (c != '\'') {
        // Non-special char (common case) means continue.
        continue;
      }
      if (pos == message.length()) {
        throw ParseException.withStartPosition("trailing single quote", message, pos - 1);
      }
      if (message.charAt(pos++) == '\'') {
        // A doubled single-quote means continue as normal.
        continue;
      }
      // Quoted mode - just scan for terminating quote.
      int quote = pos - 2;
      do {
        // If we run out of string it was badly formatted (a non-terminating quote).
        if (pos == message.length()) {
          throw ParseException.withStartPosition("unmatched single quote", message, quote);
        }
      } while (message.charAt(pos++) != '\'');
      // The last character was consumed was a quote, so we are back in unquoted mode.
    }
    return -1;
  }

  /**
   * Unescapes the characters in the sub-string {@code s.substring(start, end)} according to
   * brace formatting rules.
   */
  // VisibleForTesting
  static void unescapeBraceFormat(StringBuilder out, String message, int start, int end) {
    int pos = start;
    boolean isQuoted = false;
    while (pos < end) {
      char c = message.charAt(pos++);
      // We catch single quotes and escaped single quotes.
      if (c != '\\' && c != '\'') {
        continue;
      }
      int quoteStart = pos - 1;
      if (c == '\\') {
        // Shouldn't risk index out of bounds here because that would be a trailing single '\'.
        c = message.charAt(pos++);
        if (c != '\'') {
          continue;
        }
      }
      // Always skip the first single-quote we find.
      out.append(message, start, quoteStart);
      start = pos;
      if (pos == end) {
        break;
      }
      if (isQuoted) {
        isQuoted = false;
      } else if (message.charAt(pos) != '\'') {
        isQuoted = true;
      } else {
        // If there are two adjacent single-quotes, advance our position so we don't detect it
        // when we go back to the top of the loop (this does mean reading that same char twice
        // if it wasn't a single quote, but this is relatively rare).
        pos++;
      }
    }
    // Append the last section (if it's non empty).
    if (start < end) {
      out.append(message, start, end);
    }
  }
}
