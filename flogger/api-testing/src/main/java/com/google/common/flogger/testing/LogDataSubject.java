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

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.backend.LogData;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.LongSubject;
import com.google.common.truth.Subject;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A <a href="https://github.com/google/truth">Truth</a> subject for {@link LogData}. */
public final class LogDataSubject extends Subject {
  private static final Subject.Factory<LogDataSubject, LogData> LOG_DATA_SUBJECT_FACTORY =
      LogDataSubject::new;

  public static Subject.Factory<LogDataSubject, LogData> logData() {
    return LOG_DATA_SUBJECT_FACTORY;
  }

  public static LogDataSubject assertThat(@NullableDecl LogData logData) {
    return assertAbout(logData()).that(logData);
  }

  private final LogData actual;

  private LogDataSubject(FailureMetadata failureMetadata, @NullableDecl LogData subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  /** Asserts about the metadata of this log entry. */
  public MetadataSubject metadata() {
    return check("getMetadata()").about(MetadataSubject.metadata()).that(actual.getMetadata());
  }

  /** Asserts about the nanosecond timestamp of this log entry. */
  public LongSubject timestampNanos() {
    return check("getTimestampNanos()").that(actual.getTimestampNanos());
  }

  /**
   * Asserts that this log entry's message matches the given value. If the log statement for the
   * entry has only a single argument (no formatting), you can write
   * {@code assertLogData(e).hasMessage(value);}.
   */
  public void hasMessage(Object messageOrLiteral) {
    if (actual.getTemplateContext() == null) {
      // Expect literal argument (possibly null).
      check("getLiteralArgument()").that(actual.getLiteralArgument()).isEqualTo(messageOrLiteral);
    } else {
      // Expect message string (non null).
      check("getTemplateContext().getMessage()")
          .that(actual.getTemplateContext().getMessage())
          .isEqualTo(messageOrLiteral);
    }
  }

  /**
   * Asserts that this log entry's arguments match the given values. If the log statement for the
   * entry only a single argument (no formatting), you can write
   * {@code assertLogData(e).hasArguments();}.
   */
  public void hasArguments(Object... args) {
    List<Object> actualArgs = ImmutableList.of();
    if (actual.getTemplateContext() != null) {
      actualArgs = Arrays.asList(actual.getArguments());
    }
    check("getArguments()").that(actualArgs).containsExactly(args).inOrder();
  }

  /** Asserts that this log entry was forced. */
  public void wasForced() {
    if (!actual.wasForced()) {
      failWithActual(simpleFact("expected to be forced"));
    }
  }

  /**
   * Asserts about the log site of the log record.
   */
  public Subject logSite() {
    return check("getLogSite()").that(actual.getLogSite());
  }
}
