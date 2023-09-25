/*
 * Copyright 2023, TeamDev. All rights reserved.
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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.logging.flogger.LogSite;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Formattable;
import java.util.FormattableFlags;
import java.util.Formatter;
import java.util.Locale;

/**
 * Static utilities for classes wishing to implement their own log message formatting. None of the
 * methods here are required in a formatter, but they should help solve problems common to log
 * message formatting.
 */
public final class MessageUtils {

  // Error message for if toString() returns null.
  private static final String NULL_TOSTRING_MESSAGE = "toString() returned null";

  private MessageUtils() {}

  // It would be more "proper" to use "Locale.getDefault(Locale.Category.FORMAT)" here, but also
  // removes the capability of optimising certain formatting operations.
  static final Locale FORMAT_LOCALE = Locale.ROOT;

  /**
   * Appends log-site information in the default format, including a trailing space.
   *
   * @param logSite the log site to be appended (ingored if {@link LogSite#INVALID}).
   * @param out the destination buffer.
   * @return whether the log-site was appended.
   */
  @CanIgnoreReturnValue
  public static boolean appendLogSite(LogSite logSite, StringBuilder out) {
    if (logSite == LogSite.INVALID) {
      return false;
    }
    out.append(logSite.getClassName())
        .append('.')
        .append(logSite.getMethodName())
        .append(':')
        .append(logSite.getLineNumber());
    return true;
  }

  /**
   * Returns a string representation of the user supplied value accounting for any possible runtime
   * exceptions. This code will never fail, but may return a synthetic error string if exceptions
   * were thrown.
   *
   * @param value the value to be formatted.
   * @return a best-effort string representation of the given value, even if exceptions were thrown.
   */
  public static String safeToString(Object value) {
    try {
      return toNonNullString(value);
    } catch (RuntimeException e) {
      return getErrorString(value, e);
    }
  }

  /**
   * Returns a string representation of the user supplied value. This method should try hard to
   * return a human readable representation, possibly going beyond the default {@code toString()}
   * representation for some well defined types.
   *
   * @param value the value to be formatted (possibly null).
   * @return a non-null string representation of the given value (possibly "null").
   */
  private static String toNonNullString(Object value) {
    if (value == null) {
      return "null";
    }
    if (!value.getClass().isArray()) {
      // toString() itself can return null and surprisingly "String.valueOf(value)" doesn't handle
      // that, and we want to ensure we never return "null". We also want to distinguish a null
      // value (which is normal) from having toString() return null (which is an error).
      String s = value.toString();
      return s != null ? s : formatErrorMessageFor(value, NULL_TOSTRING_MESSAGE);
    }
    // None of the following methods can return null if given a non-null value.
    if (value instanceof int[]) {
      return Arrays.toString((int[]) value);
    }
    if (value instanceof long[]) {
      return Arrays.toString((long[]) value);
    }
    if (value instanceof byte[]) {
      return Arrays.toString((byte[]) value);
    }
    if (value instanceof char[]) {
      return Arrays.toString((char[]) value);
    }
    if (value instanceof short[]) {
      return Arrays.toString((short[]) value);
    }
    if (value instanceof float[]) {
      return Arrays.toString((float[]) value);
    }
    if (value instanceof double[]) {
      return Arrays.toString((double[]) value);
    }
    if (value instanceof boolean[]) {
      return Arrays.toString((boolean[]) value);
    }
    // Non fundamental type array.
    return Arrays.toString((Object[]) value);
  }

  /**
   * Returns a string representation of the user supplied {@link Formattable}, accounting for any
   * possible runtime exceptions.
   *
   * @param value the value to be formatted.
   * @param out the buffer into which to format it.
   * @param options the format options (extracted from a printf placeholder in the log message).
   */
  public static void safeFormatTo(Formattable value, StringBuilder out, FormatOptions options) {
    // Only care about 3 specific flags for Formattable.
    int formatFlags = options.getFlags() & (
            FormatOptions.FLAG_LEFT_ALIGN | FormatOptions.FLAG_UPPER_CASE | FormatOptions.FLAG_SHOW_ALT_FORM);
    if (formatFlags != 0) {
      // TODO: Maybe re-order the options flags to make this step easier or use a lookup table.
      // Note that reordering flags would require a rethink of how they are parsed.
      formatFlags =
          ((formatFlags & FormatOptions.FLAG_LEFT_ALIGN) != 0 ? FormattableFlags.LEFT_JUSTIFY : 0)
              | ((formatFlags & FormatOptions.FLAG_UPPER_CASE) != 0 ? FormattableFlags.UPPERCASE : 0)
              | ((formatFlags & FormatOptions.FLAG_SHOW_ALT_FORM) != 0 ? FormattableFlags.ALTERNATE : 0);
    }
    // We may need to undo an arbitrary amount of appending if there is an error.
    int originalLength = out.length();
    Formatter formatter = new Formatter(out, FORMAT_LOCALE);
    try {
      value.formatTo(formatter, formatFlags, options.getWidth(), options.getPrecision());
    } catch (RuntimeException e) {
      // Roll-back any partial changes on error, and instead append an error string for the value.
      out.setLength(originalLength);
      // We only use a StringBuilder to create the Formatter instance, which never throws.
      try {
        formatter.out().append(getErrorString(value, e));
      } catch (IOException impossible) {
        /* impossible */
      }
    }
  }

  // Visible for testing
  static void appendHex(StringBuilder out, Number number, FormatOptions options) {
    // We know there are no unexpected formatting flags (currently only upper casing is supported).
    boolean isUpper = options.shouldUpperCase();
    // We cannot just call Long.toHexString() as that would get negative values wrong.
    long n = number.longValue();
    // Roughly in order of expected usage.
    if (number instanceof Long) {
      appendHex(out, n, isUpper);
    } else if (number instanceof Integer) {
      appendHex(out, n & 0xFFFFFFFFL, isUpper);
    } else if (number instanceof Byte) {
      appendHex(out, n & 0xFFL, isUpper);
    } else if (number instanceof Short) {
      appendHex(out, n & 0xFFFFL, isUpper);
    } else if (number instanceof BigInteger) {
      String hex = ((BigInteger) number).toString(16);
      out.append(isUpper ? hex.toUpperCase(FORMAT_LOCALE) : hex);
    } else {
      // This will be caught and handled by the logger, but it should never happen.
      throw new IllegalStateException("unsupported number type: " + number.getClass());
    }
  }

  private static void appendHex(StringBuilder out, long n, boolean isUpper) {
    if (n == 0) {
      out.append("0");
    } else {
      String hexChars = isUpper ? "0123456789ABCDEF" : "0123456789abcdef";
      // Shift with a value in the range 0..60 and count down in steps of 4. You could unroll this
      // into a switch statement and it might be faster, but it's likely not worth it.
      for (int shift = (63 - Long.numberOfLeadingZeros(n)) & ~3; shift >= 0; shift -= 4) {
        out.append(hexChars.charAt((int) ((n >>> shift) & 0xF)));
      }
    }
  }

  private static String getErrorString(Object value, RuntimeException e) {
    String errorMessage;
    try {
      errorMessage = e.toString();
    } catch (RuntimeException runtimeException) {
      // Ok, now you're just being silly...
      errorMessage = runtimeException.getClass().getSimpleName();
    }
    return formatErrorMessageFor(value, errorMessage);
  }

  private static String formatErrorMessageFor(Object value, String errorMessage) {
    return "{"
        + value.getClass().getName()
        + "@"
        + System.identityHashCode(value)
        + ": "
        + errorMessage
        + "}";
  }
}
