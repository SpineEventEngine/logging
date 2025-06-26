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
 * Base class from which any specific message parsers are derived (e.g. {@link PrintfMessageParser}
 * and {@link BraceStyleMessageParser}).
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parser/MessageParser.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
public abstract class MessageParser {
  /**
   * The maximum allowed index (this should correspond to the MAX_ALLOWED_WIDTH
   * in {@link io.spine.logging.jvm.backend.FormatOptions FormatOptions}
   * because at times it is ambiguous as to which is being parsed).
   */
  public static final int MAX_ARG_COUNT = 1000000;

  /**
   * Abstract parse method implemented by specific subclasses to modify parsing behavior.
 *
   * <p>
   * Note that when extending parsing behavior, it is expected that specific parsers such as
   * {@link DefaultPrintfMessageParser} or {@link DefaultBraceStyleMessageParser} will be
   * sub-classed. Extending this class directly is only necessary when an entirely new type of
   * format needs to be supported (which should be extremely rare).
 *
   * <p>
   * Implementations of this method are required to invoke the
   * {@link MessageBuilder#addParameterImpl} method of the supplied builder once for each
   * parameter place-holder in the message.
   */
  protected abstract <T> void parseImpl(MessageBuilder<T> builder) throws ParseException;

  /**
   * Appends the unescaped literal representation of the given message string (assumed to be escaped
   * according to this parser's escaping rules). This method is designed to be invoked from a
   * callback method in a {@link MessageBuilder} instance.
   *
   * @param out the destination into which to append characters
   * @param message the escaped log message
   * @param start the start index (inclusive) in the log message
   * @param end the end index (exclusive) in the log message
   */
  public abstract void unescape(StringBuilder out, String message, int start, int end);
}
