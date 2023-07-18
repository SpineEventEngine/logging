/*
 * Copyright (C) 2012 The Flogger Authors.
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

package com.google.common.flogger.parser

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DefaultPrintfMessageParserSpec {

  private val parser = DefaultPrintfMessageParser.getInstance()

  @Test
  fun testParsePrintf() {
//    val builder = FakeMessageBuilder(parser, "message")
//    parser.parsePrintfTerm(builder, 1, "Hello %2$+06.2f World", 6, 9, 14)
//
//    // Capture the parameter created by the parsing of the printf term.
//    val param: ArgumentCaptor<Parameter> = ArgumentCaptor.forClass(Parameter.class)
//    verify(builder).addParameterImpl(eq(6), eq(15), param.capture())
//    assertThat(param.getValue().getIndex()).isEqualTo(1)
//
//    // Now visit the parameter and capture its state (doing it this way avoids needing to open up
//    // methods on the Parameter interface just for testing).
//    ParameterVisitor out = mock(ParameterVisitor.class)
//    param.getValue().accept(out, new Object[] {"Answer: ", 42.0})
//
//    // Recover the captured arguments and check that the right formatting was done.
//    ArgumentCaptor<FormatOptions> options = ArgumentCaptor.forClass(FormatOptions.class)
//    verify(out).visit(eq(42.0), eq(FormatChar.FLOAT), options.capture())
//    assertThat(options.getValue().getWidth()).isEqualTo(6)
//    assertThat(options.getValue().getPrecision()).isEqualTo(2)
//    assertThat(options.getValue().shouldShowLeadingZeros()).isTrue()
//    assertThat(options.getValue().shouldPrefixPlusForPositiveValues()).isTrue()
  }

  @Test
  fun testUnknownPrintfFormat() {
    val exception = assertThrows<ParseException> {
      parser.parsePrintfTerm(null, 0, "%Q", 0, 1, 1)
    }
    exception.message shouldContain "[%Q]"
  }

  @Test
  fun testInvalidPrintfFlags() {
    val exception = assertThrows<ParseException> {
      parser.parsePrintfTerm(null, 0, "%0s", 0, 1, 2)
    }
    exception.message shouldContain "[%0s]"
  }
}
