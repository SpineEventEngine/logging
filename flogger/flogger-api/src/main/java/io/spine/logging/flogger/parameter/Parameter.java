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

import io.spine.logging.flogger.backend.FormatOptions;

/**
 * An abstract representation of a parameter for a message template.
 * <p>
 * Note that this is implemented as a class (rather than via an interface) because it is very
 * helpful to have explicit checks for the index values and count to ensure we can calculate
 * reliable low bounds for the number of arguments a template can accept.
 * <p>
 * Note that all subclasses of Parameter must be immutable and thread safe.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/Parameter.java">
 *     Original Java code of Google Flogger</a>
 */
public abstract class Parameter {
  private final int index;
  private final FormatOptions options;

  /**
   * Constructs a parameter to format an argument using specified formatting options.
   *
   * @param options the format options for this parameter.
   * @param index the index of the argument processed by this parameter.
   */
  protected Parameter(FormatOptions options, int index) {
    if (options == null) {
      throw new IllegalArgumentException("format options cannot be null");
    }
    if (index < 0) {
      throw new IllegalArgumentException("invalid index: " + index);
    }
    this.index = index;
    this.options = options;
  }

  /** Returns the index of the argument to be processed by this parameter. */
  public final int getIndex() {
    return index;
  }

  /** Returns the formatting options. */
  protected final FormatOptions getFormatOptions() {
    return options;
  }

  public final void accept(ParameterVisitor visitor, Object[] args) {
    if (getIndex() < args.length) {
      Object value = args[getIndex()];
      if (value != null) {
        accept(visitor, value);
      } else {
        visitor.visitNull();
      }
    } else {
      visitor.visitMissing();
    }
  }

  protected abstract void accept(ParameterVisitor visitor, Object value);

  /**
   * Returns the printf format string specified for this parameter (eg, "%d" or "%tc").
   */
  public abstract String getFormat();
}
