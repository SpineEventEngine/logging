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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * A logger backend which captures all {@code LogData} instances logged to it. This class is
 * mutable and not thread safe.
 */
public final class FakeLoggerBackend extends LoggerBackend {
  private final String name;
  private Level minLevel = Level.INFO;
  private final List<LogData> logged = new ArrayList<LogData>();

  /**
   * Returns a fake backend with a fixed name. Use this constructor by default unless your tests
   * care about the backend's name (which in general, they shouldn't).
   */
  public FakeLoggerBackend() {
    this("com.example.MyClass");
  }

  /**
   * Returns a fake backend with the given name. Use this constructor only if your tests care about
   * the backend's name (which in general, they shouldn't).
   */
  public FakeLoggerBackend(String name) {
    this.name = checkNotNull(name, "name");
  }

  /** Sets the current level of this backend. */
  public void setLevel(Level level) {
    this.minLevel = checkNotNull(level, "level");
  }

  /** Returns the number of {@link LogData} entries captured by this backend. */
  public int getLoggedCount() {
    return logged.size();
  }

  /** Returns the {@code Nth} {@link LogData} entry captured by this backend. */
  public LogData getLogged(int n) {
    return logged.get(n);
  }

  /** Asserts about the {@code Nth} logged entry. */
  public LogDataSubject assertLogged(int n) {
    return LogDataSubject.assertThat(logged.get(n));
  }

  /** Asserts about the most recent logged entry. */
  public LogDataSubject assertLastLogged() {
    return assertLogged(logged.size() - 1);
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
