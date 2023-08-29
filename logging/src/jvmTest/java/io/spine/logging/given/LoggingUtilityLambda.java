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

package io.spine.logging.given;

import io.spine.logging.Logger;
import io.spine.logging.LoggingFactory;

import java.util.function.Supplier;

/**
 * A utility class that gets a logger calling
 * {@link LoggingFactory#forEnclosingClass() LoggingFactory.forEnclosingClass()}
 * from a lambda.
 */
public class LoggingUtilityLambda {

    private static final Supplier<Logger<?>> lambdaLogger = createLambdaLogger();
    private static final Logger<?> logger = lambdaLogger.get();

    /**
     * Prevents instantiation of this utility class.
     */
    private LoggingUtilityLambda() {
    }

    /**
     * Returns a logger used by this utility.
     */
    public static Logger<?> usedLogger() {
        return logger;
    }

    /**
     * Returns a class of the anonymous object used by this utility.
     */
    public static Class<?> labmdaClass() {
        return lambdaLogger.getClass();
    }

    private static Supplier<Logger<?>> createLambdaLogger() {
        return LoggingFactory::forEnclosingClass;
    }
}
