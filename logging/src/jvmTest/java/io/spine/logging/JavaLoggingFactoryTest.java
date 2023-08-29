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

import io.spine.logging.given.LoggingUtility;
import io.spine.logging.given.LoggingUtilityA;
import io.spine.logging.given.LoggingUtilityB;
import kotlin.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.logging.dynamic.backend.CaptureLogDataKt.captureLogData;

@DisplayName("In Java, `LoggingFactory` should")
class JavaLoggingFactoryTest {

    @Nested
    @DisplayName("in static context")
    class InStaticContext {

        @Test
        @DisplayName("provide a logger for the enclosing class")
        void provideLoggerForEnclosingClass() {
            var message = "some expected message";
            var logged = captureLogData(() -> {
                var logger = LoggingUtility.usedLogger();
                logger.atInfo().log(() -> message);
               return Unit.INSTANCE;
            });

            assertThat(logged.size()).isEqualTo(1);

            var logData = logged.get(0);
            assertThat(logData.getLiteralArgument()).isEqualTo(message);
            assertThat(logData.getLoggerName()).isEqualTo(LoggingUtility.class.getName());
        }

        @Test
        @DisplayName("provide the same logger for the same enclosing class")
        void provideSameLoggerForSameEnclosingClasses() {
            var logger1 = LoggingFactory.forEnclosingClass();
            var logger2 = LoggingFactory.forEnclosingClass();
            assertThat(logger1).isSameInstanceAs(logger2);
            assertThat(logger1).isEqualTo(logger2);
        }

        @Test
        @DisplayName("provide different loggers for different enclosing classes")
        void provideDifferentLoggerForDifferentEnclosingClasses() {
            var loggerA = LoggingUtilityA.usedLogger();
            var loggerB = LoggingUtilityB.usedLogger();
            assertThat(loggerA).isNotSameInstanceAs(loggerB);
            assertThat(loggerA).isNotEqualTo(loggerB);
        }
    }
}
