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

package com.google.common.flogger.util;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Flogger's own version of the Guava {@code Preconditions} class for simple, often used checks.
 */
public class Checks {
  private Checks() {}

  // Warning: The methods in this class may not use String.format() to construct "fancy" error
  // messages (because that's not GWT compatible).

  @CanIgnoreReturnValue
  public static <T> T checkNotNull(T value, String name) {
    if (value == null) {
      throw new NullPointerException(name + " must not be null");
    }
    return value;
  }

  public static void checkArgument(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  public static void checkState(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  /** Checks if the given string is a valid metadata identifier. */
  @CanIgnoreReturnValue
  public static String checkMetadataIdentifier(String s) {
    // Note that we avoid using regular expressions here, since we've not used it anywhere else
    // thus far in Flogger (avoid it make it more likely that Flogger can be transpiled).
    if (s.isEmpty()) {
      throw new IllegalArgumentException("identifier must not be empty");
    }
    if (!isLetter(s.charAt(0))) {
      throw new IllegalArgumentException("identifier must start with an ASCII letter: " + s);
    }
    for (int n = 1; n < s.length(); n++) {
      char c = s.charAt(n);
      if (!isLetter(c) && (c < '0' || c > '9') && c != '_') {
        throw new IllegalArgumentException(
            "identifier must contain only ASCII letters, digits or underscore: " + s);
      }
    }
    return s;
  }

  // WARNING: The reason the we are NOT using method from Character like "isLetter()",
  // "isJavaLetter()", "isJavaIdentifierStart()" etc. is that these rely on the Unicode definitions
  // of "LETTER", which are not stable between releases. In theory something marked as a letter in
  // Unicode could be changed to not be a letter in a later release. There is a notion of stable
  // identifiers in Unicode, which is what should be used here, but that needs more investigation.
  private static boolean isLetter(char c) {
    return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
  }
}
