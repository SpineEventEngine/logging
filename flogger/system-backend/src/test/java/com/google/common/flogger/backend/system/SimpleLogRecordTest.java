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

package com.google.common.flogger.backend.system;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.FakeLogData;
import com.google.common.flogger.testing.FakeMetadata;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleLogRecordTest {
  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);
  private static final MetadataKey<String> PATH_KEY =
      new MetadataKey<String>("path", String.class, true) {
        @Override
        public void emitRepeated(Iterator<String> values, KeyValueHandler out) {
          out.handle(getLabel(), Joiner.on('/').join(values));
        }
      };

  @Test
  public void testLiteral() {
    LogData data = FakeLogData.of("literal message").setLevel(Level.WARNING);

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
    assertThat(record.getMessage()).isEqualTo("literal message");
    assertThat(record.getParameters()).isEmpty();

    // We'll test these once here as it should be the same for all records.
    assertThat(record.getLoggerName()).isEqualTo(data.getLoggerName());
    assertThat(record.getSourceClassName()).isEqualTo(data.getLogSite().getClassName());
    assertThat(record.getSourceMethodName()).isEqualTo(data.getLogSite().getMethodName());
  }

  @Test
  public void testLiteralNull() {
    LogData data = FakeLogData.of(null).setLevel(Level.WARNING);

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
    assertThat(record.getMessage()).isEqualTo("null");
    assertThat(record.getParameters()).isEmpty();
  }

  @Test
  public void testWithArguments() {
    LogData data = FakeLogData.withBraceStyle("Answer={0}", 42).setLevel(Level.FINE);

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.FINE);
    assertThat(record.getMessage()).isEqualTo("Answer=42");
    assertThat(record.getParameters()).isEmpty();
  }

  @Test
  public void testWithPrintfFormatting() {
    LogData data = FakeLogData.withPrintfStyle("Hex=%#08x, Int=%1$d", 0xC0DE);

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage()).isEqualTo("Hex=0x00c0de, Int=49374");
    assertThat(record.getParameters()).isEmpty();
  }

  @Test
  public void testWithThrown() {
    Throwable cause = new Throwable("Goodbye World");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause);

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getThrown()).isSameInstanceAs(cause);
  }

  @Test
  public void testErrorHandling() {
    Throwable cause = new Throwable("Original Cause");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause);

    RuntimeException error = new RuntimeException("Runtime Error");
    LogRecord record = SimpleLogRecord.error(error, data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
    assertThat(record.getThrown()).isEqualTo(error);
    assertThat(record.getMessage()).contains("message: Hello World");
    // This is formatted from the original log data.
    assertThat(record.getMessage()).contains("level: INFO");
    // The original cause is in the metadata of the original log data.
    assertThat(record.getMessage()).contains("Original Cause");
  }

  @Test
  public void testWithArgumentsAndMetadata() {
    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .setTimestampNanos(123456789000L)
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID");

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage()).isEqualTo("Foo='bar' [CONTEXT count=23 id=\"test ID\" ]");
    assertThat(record.getParameters()).isEmpty();
    // Just do this once for sanity checking - it's only for debugging.
    record.setMillis(123456789);
    assertThat(record.toString())
         .isEqualTo(
            "SimpleLogRecord {\n"
                + "  message: Foo='bar' [CONTEXT count=23 id=\"test ID\" ]\n"
                + "  arguments: []\n"
                + "  original message: Foo='%s'\n"
                + "  original arguments:\n"
                + "    bar\n"
                + "  metadata:\n"
                + "    count: 23\n"
                + "    id: test ID\n"
                + "  level: INFO\n"
                + "  timestamp (nanos): 123456789000\n"
                + "  class: com.google.FakeClass\n"
                + "  method: fakeMethod\n"
                + "  line number: 123\n"
                + "}");
  }

  @Test
  public void testMetadataInScopeAndLogSite() {
    FakeMetadata scope = new FakeMetadata();
    scope.add(PATH_KEY, "foo");
    scope.add(COUNT_KEY, 23);
    scope.add(PATH_KEY, "bar");

    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .addMetadata(ID_KEY, "quux")
            .addMetadata(PATH_KEY, "baz");

    LogRecord record = SimpleLogRecord.create(data, scope);

    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage())
         .isEqualTo("Foo='bar' [CONTEXT path=\"foo/bar/baz\" count=23 id=\"quux\" ]");
  }

  @Test
  public void testWithTags() {
    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .addMetadata(
                LogContext.Key.TAGS,
                Tags.builder().addTag("foo", "FOO").addTag("bar", "BAR").addTag("baz").build());

    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());

    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage())
         .isEqualTo("Foo='bar' [CONTEXT bar=\"BAR\" baz=true foo=\"FOO\" ]");
  }

  @Test
  public void testNullArgs() {
    LogData data = FakeLogData.withPrintfStyle("value=%s", new Object[] {null});
    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());
    assertThat(record.getMessage()).isEqualTo("value=null");
  }

  @Test
  public void testMissingArgs() {
    LogData data = FakeLogData.withPrintfStyle("foo=%s, bar=%s", "FOO");
    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());
    assertThat(record.getMessage()).isEqualTo("foo=FOO, bar=[ERROR: MISSING LOG ARGUMENT]");
  }

  @Test
  public void testUnusedArgs() {
    LogData data = FakeLogData.withPrintfStyle("%2$s %s %<s %s", "a", "b", "c", "d"); // "b a a b"
    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());
    assertThat(record.getMessage()).isEqualTo("b a a b [ERROR: UNUSED LOG ARGUMENTS]");
  }

  @Test
  public void testTrailingArgsAbove32AreCaught() {
    // 33 arguments with the 33rd argument unreferenced.
    LogData data =
        FakeLogData.withPrintfStyle(
            "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s "
                + "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s",
            new Object[33]);
    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());
    assertThat(record.getMessage()).contains("[ERROR: UNUSED LOG ARGUMENTS]");
  }

  @Test
  public void testGapsInArgsUpTo32AreCaught() {
    // 33 arguments with the 32rd argument unreferenced.
    LogData data =
        FakeLogData.withPrintfStyle(
            "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s "
                + "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %33$s",
            new Object[33]);
    try {
      SimpleLogRecord.create(data, Metadata.empty());
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("unreferenced arguments [first missing index=31]");
    }
  }

  @Test
  public void testGapsInArgsAbove32AreIgnored() {
    // 34 arguments with the 33rd argument unreferenced (leaving a gap).
    LogData data =
        FakeLogData.withPrintfStyle(
            "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s "
                + "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %34$s",
            new Object[34]);
    LogRecord record = SimpleLogRecord.create(data, Metadata.empty());
    assertThat(record.getMessage()).doesNotContain("UNUSED");
  }

  @Test
  public void testToString_b31405251() {
    // Test once with arguments and once without (LogData behaves differently in each case).
    LogData data = FakeLogData.withPrintfStyle("Answer=%d", 42);
    String toString = SimpleLogRecord.create(data, Metadata.empty()).toString();
    // From the SimpleLogRecord point of view, we don't have arguments after formatting.
    assertThat(toString).contains("  message: Answer=42");
    assertThat(toString).contains("  arguments: []");
    assertThat(toString).contains("  original message: Answer=%d");

    data = FakeLogData.of("Literal String");
    toString = SimpleLogRecord.create(data, Metadata.empty()).toString();
    assertThat(toString).contains("  message: Literal String");
    assertThat(toString).contains("  arguments: []");
    assertThat(toString).contains("  original message: Literal String");
  }
}
