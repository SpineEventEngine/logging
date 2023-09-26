/*
 * Copyright 2012, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.flogger.parameter;

import static io.spine.logging.flogger.util.Checks.checkNotNull;

import io.spine.logging.flogger.backend.FormatChar;
import io.spine.logging.flogger.backend.FormatOptions;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A simple, single argument, parameter which can format arguments according to the rules specified
 * by {@link FormatChar}.
 * <p>
 * This class is immutable and thread safe, as per the Parameter contract.
 */
public final class SimpleParameter extends Parameter {
  /** Cache parameters with indices 0-9 to cover the vast majority of cases. */
  private static final int MAX_CACHED_PARAMETERS = 10;

  /** Map of the most common default general parameters (corresponds to %s, %d, %f etc...). */
  private static final Map<FormatChar, SimpleParameter[]> DEFAULT_PARAMETERS;

  static {
    Map<FormatChar, SimpleParameter[]> map =
        new EnumMap<FormatChar, SimpleParameter[]>(FormatChar.class);
    for (FormatChar fc : FormatChar.values()) {
      map.put(fc, createParameterArray(fc));
    }
    DEFAULT_PARAMETERS = Collections.unmodifiableMap(map);
  }

  /** Helper to make reusable default parameter instances for the commonest indices. */
  private static SimpleParameter[] createParameterArray(FormatChar formatChar) {
    SimpleParameter[] parameters = new SimpleParameter[MAX_CACHED_PARAMETERS];
    for (int index = 0; index < MAX_CACHED_PARAMETERS; index++) {
      parameters[index] = new SimpleParameter(index, formatChar, FormatOptions.getDefault());
    }
    return parameters;
  }

  /**
   * Returns a {@link Parameter} representing the given formatting options of the specified
   * formatting character. Note that a cached value may be returned.
   *
   * @param index the index of the argument to be processed.
   * @param formatChar the basic formatting type.
   * @param options additional formatting options.
   * @return the immutable, thread safe parameter instance.
   */
  public static SimpleParameter of(int index, FormatChar formatChar, FormatOptions options) {
    // We can safely test FormatSpec with '==' because the factory methods always return the default
    // instance if applicable (and the class has no visible constructors).
    if (index < MAX_CACHED_PARAMETERS && options.isDefault()) {
      return DEFAULT_PARAMETERS.get(formatChar)[index];
    }
    return new SimpleParameter(index, formatChar, options);
  }

  private final FormatChar formatChar;
  private final String formatString;

  private SimpleParameter(int index, FormatChar formatChar, FormatOptions options) {
    super(options, index);
    this.formatChar = checkNotNull(formatChar, "format char");
    // TODO: Consider special case for hex strings where options are common (HexParameter?).
    this.formatString = options.isDefault()
        ? formatChar.getDefaultFormatString()
        : buildFormatString(options, formatChar);
  }

  // Visible for testing.
  static String buildFormatString(FormatOptions options, FormatChar formatChar) {
    // The format char is guaranteed to be a lower-case ASCII character, so can be made upper case
    // by simply subtracting 0x20 (or clearing the 6th bit).
    char c = formatChar.getChar();
    c = options.shouldUpperCase() ? (char) (c & ~0x20) : c;
    return options.appendPrintfOptions(new StringBuilder("%")).append(c).toString();
  }

  @Override
  protected void accept(ParameterVisitor visitor, Object value) {
    visitor.visit(value, formatChar, getFormatOptions());
  }

  @Override
  public String getFormat() {
    return formatString;
  }
}
