/*
 * Copyright (C) 2014 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * Helper class for testing system backends, which need to mimic
 * the JDK's log record.
 * <p>
 * Note that we do not just capture the {@link LogRecord} because we know
 * that it is the action of calling some of the getters that causes work
 * to be done. This is what any log handler is expected to do.
 */
public class AssertingLogger extends Logger {

  private static class LogEntry {

    LogEntry(String message) {
      this.message = message;
    }

    private final String message;
  }

  private class AssertingHandler extends Handler {

    @Override
    public void publish(LogRecord r) {
      entries.add(new LogEntry(r.getMessage()));
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }

  private final List<LogEntry> entries = new ArrayList<>();

  @SuppressWarnings("OverridableMethodCallDuringObjectConstruction")
  public AssertingLogger() {
    super("", null);
    addHandler(new AssertingHandler());
    setUseParentHandlers(false);
  }

  @Override
  public boolean isLoggable(Level level) {
    return true;
  }

  /**
   * Returns the last logged message.
   *
   * <p>{@code get} prefix is preserved for this method to keep
   * it a Kotlin-compatible property.
   */
  public String getLastLogged() {
    var index = entries.size() - 1;
    return entries.get(index).message;
  }

  /**
   * Returns all logged messages.
   *
   * <p>{@code get} prefix is preserved for this method to keep
   * it a Kotlin-compatible property.
   */
  public List<String> getLogged() {
    return entries.stream()
            .map(entry -> entry.message)
            .collect(toList());
  }
}
