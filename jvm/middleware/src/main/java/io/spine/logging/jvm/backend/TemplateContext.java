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

package io.spine.logging.jvm.backend;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

import io.spine.logging.jvm.parser.MessageParser;

/**
 * A context object for templates that allows caches to validate existing templates or create new
 * ones. If two template contexts are equal (via {@link #equals}) then the templates they produce
 * are interchangeable.
 *
 * <p>
 * Template contexts are created by the frontend and passed through to backend implementations via
 * the {@link LogData} interface.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/TemplateContext.java">
 *     Original Java code of Google Flogger</a>
 */
public final class TemplateContext {
  private final MessageParser parser;
  private final String message;

  /** Creates a template context for a log statement. */
  public TemplateContext(MessageParser parser, String message) {
    this.parser = checkNotNull(parser, "parser");
    this.message = checkNotNull(message, "message");
  }

  /** Returns the message parser for the log statement. */
  public MessageParser getParser() {
    return parser;
  }

  /** Returns the message for the log statement. */
  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TemplateContext) {
      TemplateContext other = (TemplateContext) obj;
      return parser.equals(other.parser) && message.equals(other.message);
    }
    return false;
  }

  @Override
  public int hashCode() {
    // We don't expect people to be using the context as a cache key, but it should work.
    return parser.hashCode() ^ message.hashCode();
  }
}
