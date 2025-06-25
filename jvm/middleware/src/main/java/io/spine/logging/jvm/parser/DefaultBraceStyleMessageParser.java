/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

import io.spine.logging.jvm.parameter.BraceStyleParameter;

/**
 * Default implementation of the brace style message parser. Note that while the underlying parsing
 * mechanism supports the more general "{n,xxx}" form for brace format style logging, the default
 * message parser is currently limited to simple indexed place holders (e.g. "{0}"). This class
 * could easily be extended to support these trailing format specifiers.
 *
 * <p>
 * Note also that the implicit place holder syntax used by Log4J (i.e. "{}") is not currently
 * supported, however this may change. Currently an unescaped "{}" term in a log message will cause
 * a parse error, so adding support for it should not be an issue.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/DefaultBraceStyleMessageParser.java">
 *     Original Java code of Google Flogger</a>
 */
public class DefaultBraceStyleMessageParser extends BraceStyleMessageParser {
  private static final BraceStyleMessageParser INSTANCE = new DefaultBraceStyleMessageParser();

  public static BraceStyleMessageParser getInstance() {
    return INSTANCE;
  }

  private DefaultBraceStyleMessageParser() {}

  @Override
  public void parseBraceFormatTerm(
      MessageBuilder<?> builder,
      int index,
      String message,
      int termStart,
      int formatStart,
      int termEnd)
      throws ParseException {

    if (formatStart != -1) {
      // Specify the optional trailing part including leading ':' but excluding trailing '}'.
      throw ParseException.withBounds(
          "the default brace style parser does not allow trailing format specifiers",
          message,
          formatStart - 1,
          termEnd - 1);
    }
    builder.addParameter(termStart, termEnd, BraceStyleParameter.of(index));
  }
}
