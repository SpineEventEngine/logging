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
import kotlin.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.logging.testutil.TapConsoleKt.tapConsole;

@DisplayName("In Java, `LoggingFactory` should")
class JavaLoggingFactoryTest {

    @Test
    @DisplayName("provide a logger for enclosing class from a static context")
    void provideLoggerForEnclosingClassFromStaticContext() {
        var message = "expected message";
        var output = tapConsole(() -> {
            LoggingUtility.logFromStaticMethod(message);
            return Unit.INSTANCE;
        });

        var utilityName = LoggingUtility.class.getSimpleName();
        assertThat(output).contains(utilityName);
        assertThat(output).contains(message);

        var testClassName = getClass().getSimpleName();
        assertThat(output).doesNotContain(testClassName);

        var factoryClassName = LoggingFactory.class.getSimpleName();
        assertThat(output).doesNotContain(factoryClassName);
    }

    @Test
    @DisplayName("provide different loggers for different enclosing classes")
    void provideDifferentLoggerForDifferentEnclosingClasses() {
        var utilityLogger = LoggingUtility.logger();
        var anotherUtilityLogger = AnotherLoggingUtility.logger();
        assertThat(utilityLogger).isNotEqualTo(anotherUtilityLogger);
    }
}
