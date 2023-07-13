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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.flogger.backend.FormatType;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A <a href="https://github.com/google/truth">Truth</a> subject for {@link FormatType}.
 *
 * @author Kurt Alfred Kluever (kak@google.com)
 */
public final class FormatTypeSubject extends Subject {

  public static FormatTypeSubject assertThat(@NullableDecl FormatType formatType) {
    return assertAbout(FormatTypeSubject.FORMAT_TYPE_SUBJECT_FACTORY).that(formatType);
  }

  private static final Subject.Factory<FormatTypeSubject, FormatType> FORMAT_TYPE_SUBJECT_FACTORY =
      FormatTypeSubject::new;

  private final FormatType actual;

  private FormatTypeSubject(FailureMetadata failureMetadata, @NullableDecl FormatType subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  public void canFormat(Object arg) {
    assertWithMessage("Unable to format " + arg + " using " + actual)
         .that(actual.canFormat(arg))
         .isTrue();
  }

  public void cannotFormat(Object arg) {
    assertWithMessage("Expected error when formatting " + arg + " using " + actual)
         .that(actual.canFormat(arg))
         .isFalse();
  }

  public void isNumeric() {
    check("isNumeric()")
        .withMessage("Expected " + actual + " to be numeric but wasn't")
        .that(actual.isNumeric())
        .isTrue();
  }

  public void isNotNumeric() {
    check("isNumeric()")
        .withMessage("Expected " + actual + " to not be numeric but was")
        .that(actual.isNumeric())
        .isFalse();
  }
}
