/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.logging

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * An annotation for classes which define a logging domain for loggers
 * created for the class.
 *
 * Logging statements for classes with the same logging domain are
 * [prefixed][LoggingDomain.messagePrefix] with the name of the logging
 * domain in square brackets.
 */
@Target(CLASS)
@Retention
public annotation class LoggingDomain(public val name: String) {

    public companion object {

        /**
         * A no-op instance of `LoggingDomain` returned for classes
         * without an associated logging domain.
         */
        public val noOp: LoggingDomain = LoggingDomain("")
    }
}

/**
 * Obtains the string to be prepended before logging statements for the classes
 * [belonging][LoggingFactory.loggingDomainOf] to this `LoggingDomain`.
 *
 * If the logging domain is not defined for a class, logging statements for it
 * will not be prefixed. Otherwise, the prefix would be the name of the logging domain
 * in square brackets followed by a space.
 *
 * If the receiver is `null`, the prefix is empty.
 */
public val LoggingDomain?.messagePrefix: String
    get() = if (this?.name?.isEmpty() != false) "" else "[$name] "
