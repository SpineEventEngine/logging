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

import io.spine.logging.given.AnotherLoggingUtility;
import io.spine.logging.given.LoggingUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;

@DisplayName("In Java, `LoggingFactory` should")
class JavaLoggingFactoryTest {

    @Nested
    @DisplayName("in static context")
    class InStaticContext {

        /**
         * Tests that the logger was created for the expected outer class.
         *
         * <p>This test uses the protected {@link Logger#getCls() Logger.cls} property
         * to assert the outer class. It is because Java can access {@code protected}
         * members from other classes in the same package (while Kotlin doesn't),
         * so Java classes have broader access to the code.
         */
        @Test
        @DisplayName("provide a logger for enclosing class from a static context")
        void provideLoggerForEnclosingClass() {
            var logger = LoggingUtility.logger();
            var kClass = getKotlinClass(LoggingUtility.class);
            assertThat(logger.getCls()).isSameInstanceAs(kClass);
        }

        @Test
        @DisplayName("provide the same logger for the same enclosing class")
        void provideSameLoggerForSameEnclosingClasses() {
            var logger1 = LoggingFactory.forEnclosingClass();
            var logger2 = LoggingFactory.forEnclosingClass();
            assertThat(logger1).isSameInstanceAs(logger2);
        }

        @Test
        @DisplayName("provide different loggers for different enclosing classes")
        void provideDifferentLoggerForDifferentEnclosingClasses() {
            var utilityLogger = LoggingUtility.logger();
            var anotherUtilityLogger = AnotherLoggingUtility.logger();
            assertThat(utilityLogger).isNotEqualTo(anotherUtilityLogger);
        }
    }
}
