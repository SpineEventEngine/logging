/*
 * Copyright 2020, The Flogger Authors; 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.logging.flogger.backend;

import static io.spine.logging.flogger.backend.FormatOptions.FLAG_UPPER_CASE;
import static io.spine.logging.flogger.util.Checks.checkNotNull;

import io.spine.logging.flogger.parameter.DateTimeFormat;
import io.spine.logging.flogger.parameter.Parameter;
import io.spine.logging.flogger.parameter.ParameterVisitor;
import io.spine.logging.flogger.parser.MessageBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Calendar;
import java.util.Date;
import java.util.Formattable;

/**
 * The default formatter for log messages and arguments.
 *
 * <p>This formatter can be overridden to modify the behaviour of the {@link ParameterVisitor}
 * methods, but this is not expected to be common. Most logger backends will only ever need to use
 * {@link #appendFormattedMessage(LogData, StringBuilder)}.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/BaseMessageFormatter.java">
 *     Original Java code of Google Flogger</a>
 */
public class BaseMessageFormatter extends MessageBuilder<StringBuilder>
    implements ParameterVisitor {

  // Literal string to be inlined whenever a placeholder references a non-existent argument.
  private static final String MISSING_ARGUMENT_MESSAGE = "[ERROR: MISSING LOG ARGUMENT]";

  // Literal string to be appended wherever additional unused arguments are provided.
  private static final String EXTRA_ARGUMENT_MESSAGE = " [ERROR: UNUSED LOG ARGUMENTS]";

  /**
   * Appends the formatted log message of the given log data to the given buffer.
   *
   * <p>Note that the {@link LogData} need not have a template context or arguments, it might just
   * have a literal argument, which will be appended without additional formatting.
   *
   * @param data the log data with the message to be appended.
   * @param out a buffer to append to.
   * @return the given buffer (for method chaining).
   */
  @CanIgnoreReturnValue
  public static StringBuilder appendFormattedMessage(LogData data, StringBuilder out) {
    if (data.getTemplateContext() != null) {
      BaseMessageFormatter formatter =
          new BaseMessageFormatter(data.getTemplateContext(), data.getArguments(), out);
      out = formatter.build();
      if (data.getArguments().length > formatter.getExpectedArgumentCount()) {
        // TODO(dbeaumont): Do better and look at adding formatted values or maybe just a count?
        out.append(EXTRA_ARGUMENT_MESSAGE);
      }
    } else {
      out.append(MessageUtils.safeToString(data.getLiteralArgument()));
    }
    return out;
  }

  // Input argument array reference (not copied).
  protected final Object[] args;
  // Buffer into which the message is formatted.
  protected final StringBuilder out;
  // The start of the next literal sub-section of the message that needs processing.
  private int literalStart = 0;

  protected BaseMessageFormatter(TemplateContext context, Object[] args, StringBuilder out) {
    super(context);
    this.args = checkNotNull(args, "arguments");
    this.out = checkNotNull(out, "buffer");
  }

  private static void appendFormatted(
      StringBuilder out, Object value, FormatChar format, FormatOptions options) {
    // Fast path switch statement for commonest cases (we could handle upper-case as a post
    // processing step but it's so uncommon it doesn't seem worth it).
    //
    // Cases and logic within cases are strictly ordered by likelihood to reduce branching (e.g.
    // normal String formatting corresponding to "%s" comes before worrying about Formattable, which
    // is hardly ever used).
    //
    // Case statements should consist of a series of if-statements, with code blocks ordered by
    // likelihood (each of which returns with a result) and a single final 'break' statement to fall
    // through to the general case logic for anything otherwise unhandled.
    //
    // Most non-default format options (e.g. "%02d") or rare format specifiers are handled by
    // breaking from the switch statement, which falls into the generic formatting logic. Anything
    // handled explicitly should return instead.
    switch (format) {
      case STRING:
        // String formatting is by far and away the most common case.
        if (!(value instanceof Formattable)) {
          if (options.isDefault()) {
            // %s on a non-Formattable instance is the single most common case by far.
            out.append(MessageUtils.safeToString(value));
            return;
          }
          break;
        }
        // Rare but easy to deal with efficiently, and a can support wrapped arguments nicely.
        MessageUtils.safeFormatTo((Formattable) value, out, options);
        return;

        // Some other types are really easy when they don't have special format options.
      case DECIMAL:
      case BOOLEAN:
        if (options.isDefault()) {
          out.append(value);
          return;
        }
        break;

      case HEX:
        // Check that if the format options are compatible with "easy" hex formatting. This could
        // be expanded to include width, radix and zero padding (relatively common for hex).
        if (options.filter(FLAG_UPPER_CASE, false, false).equals(options)) {
          // Since canFormat() was called before this method, the value must be a Number.
          MessageUtils.appendHex(out, (Number) value, options);
          return;
        }
        break;

      case CHAR:
        // %c/%C formatting is a little subtle since an Integer or Long can represent a code-point
        // resulting in more than one UTF-16 "char".
        if (options.isDefault()) {
          if (value instanceof Character) {
            out.append(value);
            return;
          }
          // Since canFormat() was called before this method, value must be a non-negative Number.
          int codePoint = ((Number) value).intValue();
          if ((codePoint >>> 16) == 0) {
            out.append((char) codePoint);
            return;
          }
          out.append(Character.toChars(codePoint));
          return;
        }
        break;

      default:
        // Fall through.
    }
    // Default handle for rare cases that need non-trivial formatting.
    String formatString = format.getDefaultFormatString();
    if (!options.isDefault()) {
      char chr = format.getChar();
      if (options.shouldUpperCase()) {
        // Clear 6th bit to convert lower case ASCII to upper case.
        chr &= (char) ~0x20;
      }
      formatString = options.appendPrintfOptions(new StringBuilder("%")).append(chr).toString();
    }
    out.append(String.format(MessageUtils.FORMAT_LOCALE, formatString, value));
  }

  @Override
  public void addParameterImpl(int termStart, int termEnd, Parameter param) {
    getParser().unescape(out, getMessage(), literalStart, termStart);
    param.accept(this, args);
    literalStart = termEnd;
  }

  @Override
  public StringBuilder buildImpl() {
    getParser().unescape(out, getMessage(), literalStart, getMessage().length());
    return out;
  }

  @Override
  public void visit(Object value, FormatChar format, FormatOptions options) {
    if (format.getType().canFormat(value)) {
      appendFormatted(out, value, format, options);
    } else {
      BaseMessageFormatter.appendInvalid(out, value, format.getDefaultFormatString());
    }
  }

  @Override
  public void visitDateTime(Object value, DateTimeFormat format, FormatOptions options) {
    if (value instanceof Date || value instanceof Calendar || value instanceof Long) {
      String formatString =
          options
              .appendPrintfOptions(new StringBuilder("%"))
              .append(options.shouldUpperCase() ? 'T' : 't')
              .append(format.getChar())
              .toString();
      out.append(String.format(MessageUtils.FORMAT_LOCALE, formatString, value));
    } else {
      BaseMessageFormatter.appendInvalid(out, value, "%t" + format.getChar());
    }
  }

  @Override
  public void visitPreformatted(Object value, String formatted) {
    // For unstructured logging we just use the pre-formatted string.
    out.append(formatted);
  }

  @Override
  public void visitMissing() {
    out.append(MISSING_ARGUMENT_MESSAGE);
  }

  @Override
  public void visitNull() {
    out.append("null");
  }

  private static void appendInvalid(StringBuilder out, Object value, String formatString) {
    out.append("[INVALID: format=")
        .append(formatString)
        .append(", type=")
        .append(value.getClass().getCanonicalName())
        .append(", value=")
        .append(MessageUtils.safeToString(value))
        .append("]");
  }
}
