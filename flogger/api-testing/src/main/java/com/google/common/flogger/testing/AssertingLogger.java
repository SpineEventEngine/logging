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

package com.google.common.flogger.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Helper class for testing system backends, which need to mimic the JDK's log record.
 * <p>
 * Note that we do not just capture the LogRecord because we know that it's the action of calling
 * some of the getters that causes work to be done (which is what any log handler would be
 * expected to do).
 */
public class AssertingLogger extends Logger {

  private static class LogEntry {
    LogEntry(String message, Object[] parameters, Level level, Throwable thrown) {
      this.message = message;
      this.parameters = parameters;
      this.level = level;
      this.thrown = thrown;
    }

    private final String message;
    private final Object[] parameters;
    private final Level level;
    private final Throwable thrown;
  }

  private class AssertingHandler extends Handler {
    @Override
    public void publish(LogRecord r) {
      entries.add(new LogEntry(r.getMessage(), r.getParameters(), r.getLevel(), r.getThrown()));
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  private final List<AssertingLogger.LogEntry> entries = new ArrayList<AssertingLogger.LogEntry>();

  public AssertingLogger() {
    super("", null);
    addHandler(new AssertingHandler());
    setUseParentHandlers(false);
  }

  @Override
  public boolean isLoggable(Level level) {
    return true;
  }

  public String getMessage(int index) {
    return entries.get(index).message;
  }

  public void assertLogCount(int count) {
    assertThat(entries).hasSize(count);
  }

  public void assertLogEntry(int index, Level level, String message, Object... parameters) {
    AssertingLogger.LogEntry entry = entries.get(index);
    assertThat(entry.level).isEqualTo(level);
    assertThat(entry.message).isEqualTo(message);
    if (parameters.length == 0) {
      if (entry.parameters != null) {
        assertThat(entry.parameters).isEmpty();
      }
    } else {
      assertWithMessage(
              "Wrong number of parameters: expected=%s, actual=%s",
              Arrays.asList(parameters),
              Arrays.asList(entry.parameters))
           .that(entry.parameters.length)
           .isEqualTo(parameters.length);
      for (int n = 0; n < parameters.length; n++) {
        // Check parameters for "weak" equality (up to the point that they format the same).
        Object expected = parameters[n];
        Object actual = entry.parameters[n];
        if (expected == null) {
          // null arguments are permitted.
          assertThat(actual).isNull();
        } else {
          // We expect a non-null argument.
          assertThat(actual).isNotNull();
          if (expected.getClass() == actual.getClass()) {
            // Same class implies the argument was not wrapped and should be equivalent.
            assertThat(actual).isEqualTo(expected);
          } else {
            // A different class implies the argument was wrapped for formatting.
            assertThat(actual.toString()).isEqualTo(expected.toString());
          }
        }
      }
    }
  }

  public void assertThrown(int index, Throwable thrown) {
    assertThat(entries.get(index).thrown).isSameInstanceAs(thrown);
  }
}
