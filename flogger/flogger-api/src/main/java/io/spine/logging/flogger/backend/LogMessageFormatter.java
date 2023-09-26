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

import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * API for formatting Flogger log messages from logData and scoped metadata.
 *
 * <p>This API is not used directly in the core Flogger libraries yet, but will become part of the
 * log message formatting API eventually. For now it should be considered an implementation detail
 * and definitely unstable.
 */
// TODO(dbeaumont): This needs to either move into "system" or be extended somehow.
// This is currently tightly coupled with the JDK log handler behaviour (by virtue of what data is
// expected to be used for formatting) so it is not suitable as a general purpose API yet.
public abstract class LogMessageFormatter {
  /**
   * Returns a formatted representation of the log message and metadata. Currently this class is
   * only responsible for formatting the main body of the log message and not thing like log site,
   * timestamps or thread information.
   *
   * <p>By default this method just returns:
   *
   * <pre>{@code append(logData, metadata, new StringBuilder()).toString()}</pre>
   *
   * <p>Formatter implementations may be able to implement it more efficiently (e.g. if they can
   * safely detect when no formatting is required). See also the helper methods in {@link
   * SimpleMessageFormatter}.
   */
  public String format(LogData logData, MetadataProcessor metadata) {
    return append(logData, metadata, new StringBuilder()).toString();
  }

  /**
   * Formats the log message and metadata into the given buffer. Currently this class is only
   * responsible for formatting the main body of the log message and not thing like log site,
   * timestamps or thread information.
   *
   * @return the given buffer for method chaining.
   */
  @CanIgnoreReturnValue
  public abstract StringBuilder append(
      LogData logData, MetadataProcessor metadata, StringBuilder buffer);
}
