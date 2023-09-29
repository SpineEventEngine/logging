/*
 * Copyright (C) 2016 The Flogger Authors.
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

import static io.spine.logging.flogger.util.Checks.checkNotNull;
import static java.util.Collections.unmodifiableList;

import io.spine.logging.flogger.backend.LogData;
import io.spine.logging.flogger.backend.LoggerBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * A logger backend, which captures all {@code LogData} instances logged to it.
 *
 * <p>This class is mutable and not thread safe.
 *
 * @see <a href="http://rb.gy/r6jjw">Original Java code of Google Flogger</a>
 */
public final class FakeLoggerBackend extends LoggerBackend {
  private final String name;
  private Level minLevel = Level.INFO;
  private final List<LogData> logged = new ArrayList<>();
  private final List<LogData> unmodifiableLogged = unmodifiableList(logged);

  /**
   * Returns a fake backend with a fixed name.
   *
   * <p>Use this constructor by default unless your tests care about
   * the backend's name (which in general, they shouldn't).
   */
  public FakeLoggerBackend() {
    this("com.example.MyClass");
  }

  /**
   * Returns a fake backend with the given name.
   *
   * <p>Use this constructor only if your tests care about the backend's name.
   * For example, when testing a logger factory that should create logger for
   * the given class. Then the class name is used for backend name.
   */
  public FakeLoggerBackend(String name) {
    this.name = checkNotNull(name, "name");
  }

  /**
   * Sets the current level of this backend.
   */
  @SuppressWarnings("DuplicateStringLiteralInspection")
  public void setLevel(Level level) {
    this.minLevel = checkNotNull(level, "level");
  }

  /**
   *  Returns the number of {@link LogData} entries captured by this backend.
   */
  public int getLoggedCount() {
    return logged.size();
  }

  /**
   * Returns all captured {@link LogData}s.
   */
  public List<LogData> getLogged() {
    return unmodifiableLogged;
  }

  /**
   * Returns the last {@link LogData} entry captured by this backend.
   *
   * @throws IllegalStateException when the backend hasn't captured any log entry
   */
  public LogData getLastLogged() {
    if (logged.isEmpty()) {
      throwNoEntries();
    }
    var index = logged.size() - 1;
    return logged.get(index);
  }

  /**
   * Returns the first {@link LogData} entry captured by this backend.
   *
   * @throws IllegalStateException when the backend hasn't captured any log entry
   */
  public LogData getFirstLogged() {
    if (logged.isEmpty()) {
      throwNoEntries();
    }
    return logged.get(0);
  }

  private static void throwNoEntries() {
    throw new IllegalStateException("Fake backend has not captured any log data.");
  }

  @Override public String getLoggerName() {
    return name;
  }

  @Override public boolean isLoggable(Level loggedLevel) {
    return loggedLevel.intValue() >= minLevel.intValue();
  }

  @Override public void log(LogData data) {
    logged.add(data);
  }

  @Override public void handleError(RuntimeException error, LogData badData) {
    throw error;
  }
}
