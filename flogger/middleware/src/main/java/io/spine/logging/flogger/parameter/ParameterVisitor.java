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

package io.spine.logging.flogger.parameter;

import io.spine.logging.flogger.backend.FormatChar;
import io.spine.logging.flogger.backend.FormatOptions;

/**
 * A visitor of log message arguments, dispatched by {@code Parameter} instances.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/ParameterVisitor.java">
 *     Original Java code of Google Flogger</a>
 */
// TODO: When all other refactoring done, rename to ArgumentVisitor
public interface ParameterVisitor {
  /**
   * Visits a log message argument with formatting specified by {@code %s}, {@code %d} etc...
   * <p>
   * Note that this method may still visit arguments which represent date/time values if the format
   * is not explicit (e.g. {@code log("time=%s", dateTime)}).
   *
   * @param value the non-null log message argument.
   * @param format the printf format specifier.
   * @param options formatting options.
   */
  void visit(Object value, FormatChar format, FormatOptions options);

  /**
   * Visits a date/time log message argument with formatting specified by {@code %t} or similar.
   * <p>
   * Note that because this method is called based on the specified format (and not the argument
   * type) it may visit arguments whose type is not a known date/time value. This is necessary to
   * permit new date/time types to be supported by different logging backends (e.g. JodaTime).
   *
   * @param value the non-null log message argument.
   * @param format the date/time format specifier.
   * @param options formatting options.
   */
  void visitDateTime(Object value, DateTimeFormat format, FormatOptions options);

  /**
   * Visits a log message argument for which formatting has already occurred. This method is only
   * invoked when non-printf message formatting is used (e.g. brace style formatting).
   * <p>
   * This method is intended for use by {@code Parameter} implementations which describe formatting
   * rules which cannot by represented by either {@link FormatChar} or {@link DateTimeFormat}. This
   * method discards formatting and type information, and the visitor implementation may choose to
   * reexamine the type of the original argument if doing structural logging.
   *
   * @param value the original non-null log message argument.
   * @param formatted the formatted representation of the argument
   */
  void visitPreformatted(Object value, String formatted);

  /**
   * Visits a missing argument. This method is called when there is no corresponding value for the
   * parameter's argument index.
   */
  void visitMissing();

  /** Visits a null argument. */
  void visitNull();
}
