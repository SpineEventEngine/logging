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

package io.spine.logging.flogger.parser;

import io.spine.logging.flogger.backend.TemplateContext;
import io.spine.logging.flogger.parameter.Parameter;
import io.spine.logging.flogger.util.Checks;

/**
 * A builder which is used during message parsing to create a message object which encapsulates
 * all the formatting requirements of a log message. One message builder is created for each log
 * message that's parsed.
 *
 * @param <T> The message type being built.
 */
public abstract class MessageBuilder<T> {
  private final TemplateContext context;

  // Mask of parameter indices seen during parsing, used to determine if there are gaps in the
  // specified parameters (which is a parsing error).
  // This could be a long if we cared about tracking up to 64 parameters, but I suspect we don't.
  private int pmask = 0;

  // The maximum argument index referenced by the formatted message (only valid after parsing).
  private int maxIndex = -1;

  public MessageBuilder(TemplateContext context) {
    this.context = Checks.checkNotNull(context, "context");
  }

  /** Returns the parser used to process the log format message in this builder. */
  public final MessageParser getParser() {
    return context.getParser();
  }

  /** Returns the log format message to be parsed by this builder. */
  public final String getMessage() {
    return context.getMessage();
  }

  /**
   * Returns the expected number of arguments to be formatted by this message. This is only valid
   * once parsing has completed successfully.
   */
  public final int getExpectedArgumentCount() {
    return maxIndex + 1;
  }

  /**
   * Called by parser implementations to signify that the parsing of the next parameter is complete.
   * This method will call {@link #addParameterImpl(int, int, Parameter)} with exactly the same
   * arguments, but may also do additional work before or after that call.
   *
   * @param termStart the index of the first character in the log message string that was parsed to
   *     form the given parameter.
   * @param termEnd the index after the last character in the log message string that was parsed to
   *     form the given parameter.
   * @param param a parameter representing the format specified by the substring of the log message
   *     in the range {@code [termStart, termEnd)}.
   */
  public final void addParameter(int termStart, int termEnd, Parameter param) {
    // Set a bit in the parameter mask according to which parameter was referenced.
    // Shifting wraps, so we must do a check here.
    if (param.getIndex() < 32) {
      pmask |= (1 << param.getIndex());
    }
    maxIndex = Math.max(maxIndex, param.getIndex());
    addParameterImpl(termStart, termEnd, param);
  }

  /**
   * Adds the specified parameter to the format instance currently being built. This method is to
   * signify that the parsing of the next parameter is complete.
   * <p>
   * Note that each successive call to this method during parsing will specify a disjoint ranges of
   * characters from the log message and that each range will be higher that the previously
   * specified one.
   *
   * @param termStart the index of the first character in the log message string that was parsed to
   *     form the given parameter.
   * @param termEnd the index after the last character in the log message string that was parsed to
   *     form the given parameter.
   * @param param a parameter representing the format specified by the substring of the log message
   *     in the range {@code [termStart, termEnd)}.
   */
  protected abstract void addParameterImpl(int termStart, int termEnd, Parameter param);

  /** Returns the implementation specific result of parsing the current log message. */
  protected abstract T buildImpl();

  /**
   * Builds a log message using the current message context.
   *
   * @return the implementation specific result of parsing the current log message.
   */
  public final T build() {
    getParser().parseImpl(this);
    // There was a gap in the parameters if either:
    // 1) the mask had a gap, e.g. ..00110111
    // 2) there were more than 32 parameters and the mask wasn't full.
    // Gaps above the 32nd parameter are not detected.
    if ((pmask & (pmask + 1)) != 0 || (maxIndex > 31 && pmask != -1)) {
      int firstMissing = Integer.numberOfTrailingZeros(~pmask);
      throw ParseException.generic(
          String.format("unreferenced arguments [first missing index=%d]", firstMissing),
          getMessage());
    }
    return buildImpl();
  }
}
