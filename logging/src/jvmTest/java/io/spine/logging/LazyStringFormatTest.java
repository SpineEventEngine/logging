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

package io.spine.logging;

import kotlin.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.logging.LazyStringFormat.lazyFormat;

@DisplayName("`LazyStringFormatTest` should")
class LazyStringFormatTest {

    @Test
    @DisplayName("provide a shortcut for `String.format()`")
    void provideShortcutToStringFormat() {
        var number = 123321;
        var expectedMsg = "Integer: " + number;
        var loggingInstance = new LoggingClass();
        var output = TapConsoleKt.tapConsole(() -> {
            loggingInstance.myMethod("Integer: %d", number);
            return Unit.INSTANCE;
        });

        assertThat(output).contains(expectedMsg);
        assertThat(output).contains(LazyStringFormatTest.LoggingClass.class.getSimpleName());
    }

    private static class LoggingClass implements WithLogging {

        @SuppressWarnings("SameParameterValue") // It is better to pass parameters explicitly.
        private void myMethod(String format, Object... args) {
            logger().atWarning().log(lazyFormat(format, args));
        }
    }
}
