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

import com.google.common.flogger.backend.FormatChar;
import com.google.common.flogger.backend.FormatOptions;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A <a href="https://github.com/google/truth">Truth</a> subject for {@link FormatOptions}.
 *
 * @author Kurt Alfred Kluever (kak@google.com)
 */
public final class FormatOptionsSubject extends Subject {

  public static FormatOptionsSubject assertThat(@NullableDecl FormatOptions formatOptions) {
    return assertAbout(FormatOptionsSubject.FORMAT_OPTIONS_FACTORY).that(formatOptions);
  }

  private static final Subject.Factory<FormatOptionsSubject, FormatOptions> FORMAT_OPTIONS_FACTORY =
      FormatOptionsSubject::new;

  private final FormatOptions actual;

  private FormatOptionsSubject(FailureMetadata failureMetadata, @NullableDecl FormatOptions subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  public void isDefault() {
    if (!actual.isDefault()) {
      failWithActual(simpleFact("expected to be default"));
    }
  }

  public void hasPrecision(int precision) {
    check("getPrecision()").that(actual.getPrecision()).isEqualTo(precision);
  }

  public void hasWidth(int width) {
    check("getWidth()").that(actual.getWidth()).isEqualTo(width);
  }

  public void hasNoFlags() {
    if (actual.getFlags() != 0) {
      failWithActual(simpleFact("expected to have no flags"));
    }
  }

  public void shouldUpperCase() {
    if (!actual.shouldUpperCase()) {
      failWithActual(simpleFact("expected to upper case"));
    }
  }

  public void shouldntUpperCase() {
    if (actual.shouldUpperCase()) {
      failWithActual(simpleFact("expected not to upper case"));
    }
  }

  public void shouldLeftAlign() {
    if (!actual.shouldLeftAlign()) {
      failWithActual(simpleFact("expected to left align"));
    }
  }

  public void shouldntLeftAlign() {
    if (actual.shouldLeftAlign()) {
      failWithActual(simpleFact("expected not to left align"));
    }
  }

  public void shouldShowAltForm() {
    if (!actual.shouldShowAltForm()) {
      failWithActual(simpleFact("expected to show alt form"));
    }
  }

  public void shouldntShowAltForm() {
    if (actual.shouldShowAltForm()) {
      failWithActual(simpleFact("expected not to show alt form"));
    }
  }

  public void shouldShowGrouping() {
    if (!actual.shouldShowGrouping()) {
      failWithActual(simpleFact("expected to show grouping"));
    }
  }

  public void shouldntShowGrouping() {
    if (actual.shouldShowGrouping()) {
      failWithActual(simpleFact("expected not to show grouping"));
    }
  }

  public void shouldShowLeadingZeros() {
    if (!actual.shouldShowLeadingZeros()) {
      failWithActual(simpleFact("expected to show leading zeros"));
    }
  }

  public void shouldntShowLeadingZeros() {
    if (actual.shouldShowLeadingZeros()) {
      failWithActual(simpleFact("expected not to show leading zeros"));
    }
  }

  public void shouldPrefixSpaceForPositiveValues() {
    if (!actual.shouldPrefixSpaceForPositiveValues()) {
      failWithActual(simpleFact("expected to prefix space for positive values"));
    }
  }

  public void shouldntPrefixSpaceForPositiveValues() {
    if (actual.shouldPrefixSpaceForPositiveValues()) {
      failWithActual(simpleFact("expected not to prefix space for positive values"));
    }
  }

  public void shouldPrefixPlusForPositiveValues() {
    if (!actual.shouldPrefixPlusForPositiveValues()) {
      failWithActual(simpleFact("expected to prefix plus for positive values"));
    }
  }

  public void shouldntPrefixPlusForPositiveValues() {
    if (actual.shouldPrefixPlusForPositiveValues()) {
      failWithActual(simpleFact("expected not to prefix plus for positive values"));
    }
  }

  public void areValidFor(FormatChar formatChar) {
    if (!actual.areValidFor(formatChar)) {
      failWithActual("expected to be valid for", formatChar);
    }
  }

  public void areNotValidFor(FormatChar formatChar) {
    if (actual.areValidFor(formatChar)) {
      failWithActual("expected not to be valid for", formatChar);
    }
  }
}
