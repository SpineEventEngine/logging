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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.Metadata;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A <a href="https://github.com/google/truth">Truth</a> subject for {@link Metadata}. */
public final class MetadataSubject extends Subject {
  private static final Subject.Factory<MetadataSubject, Metadata> METADATA_SUBJECT_FACTORY =
      MetadataSubject::new;

  public static Subject.Factory<MetadataSubject, Metadata> metadata() {
    return METADATA_SUBJECT_FACTORY;
  }

  public static MetadataSubject assertThat(@NullableDecl Metadata metadata) {
    return assertAbout(metadata()).that(metadata);
  }

  private final Metadata actual;

  private MetadataSubject(FailureMetadata failureMetadata, @NullableDecl Metadata subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  private List<MetadataKey<?>> keyList() {
    Metadata metadata = actual;
    List<MetadataKey<?>> keys = new ArrayList<>();
    for (int n = 0; n < metadata.size(); n++) {
      keys.add(metadata.getKey(n));
    }
    return keys;
  }

  private List<Object> valueList() {
    Metadata metadata = actual;
    List<Object> values = new ArrayList<>();
    for (int n = 0; n < metadata.size(); n++) {
      values.add(metadata.getValue(n));
    }
    return values;
  }

  private <T> List<T> valuesOf(MetadataKey<T> key) {
    Metadata metadata = actual;
    List<T> values = new ArrayList<>();
    for (int n = 0; n < metadata.size(); n++) {
      if (metadata.getKey(n).equals(key)) {
        values.add(key.cast(metadata.getValue(n)));
      }
    }
    return values;
  }

  public void hasSize(int expectedSize) {
    checkArgument(expectedSize >= 0, "expectedSize(%s) must be >= 0", expectedSize);
    check("size()").that(actual.size()).isEqualTo(expectedSize);
  }

  public <T> void containsUniqueEntry(MetadataKey<T> key, T value) {
    checkNotNull(key, "key must not be null");
    checkNotNull(value, "value must not be null");
    T actual = this.actual.findValue(key);
    if (actual == null) {
      failWithActual("expected to contain value for key", key);
    } else {
      check("findValue(%s)", key).that(actual).isEqualTo(value);
      // The key must exist, so neither method will return -1.
      List<MetadataKey<?>> keys = keyList();
      if (keys.indexOf(key) != keys.lastIndexOf(key)) {
        failWithActual("expected to have unique key", key);
      }
    }
  }

  public <T> void containsEntries(MetadataKey<T> key, T... values) {
    checkNotNull(key, "key must not be null");
    check("<values of>(%s)", key).that(valuesOf(key)).containsExactlyElementsIn(values).inOrder();
  }

  public IterableSubject keys() {
    return check("keys()").that(keyList());
  }

  public IterableSubject values() {
    return check("values()").that(valueList());
  }
}

