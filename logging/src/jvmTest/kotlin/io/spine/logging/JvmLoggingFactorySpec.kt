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

package io.spine.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.LoggingFactory.loggingDomainOf
import io.spine.logging.given.domain.AnnotatedClass
import io.spine.logging.given.domain.IndirectlyAnnotatedClass
import io.spine.logging.given.domain.nested.NonAnnotatedNestedPackageClass
import io.spine.logging.testutil.tapConsole
import kotlin.reflect.jvm.jvmName
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`JvmLoggingFactory` should")
internal class JvmLoggingFactorySpec {

    @Test
    fun `provide a logger for enclosing class`() {
        val logger = LoggingFactory.forEnclosingClass()
        val message = "expected message"
        val output = tapConsole {
            logger.atInfo().log { message }
        }
        output shouldContain this::class.jvmName
    }

    @Test
    fun `provide the same logger for the same enclosing class`() {
        val firstLogger = LoggingFactory.forEnclosingClass()
        val secondLogger = LoggingFactory.forEnclosingClass()
        firstLogger shouldBeSameInstanceAs secondLogger
    }

    @Nested
    inner class `obtain a logging domain for` {

        @Test
        fun `directly annotated class`() {
            val loggingDomain = loggingDomainOf(AnnotatedClass::class)
            loggingDomain.name shouldBe "OnClass"
        }

        @Test
        fun `a class with annotated package`() {
            val loggingDomain = loggingDomainOf(IndirectlyAnnotatedClass::class)
            loggingDomain.name shouldBe "OnPackage"
        }

        @Test
        fun `a class in a nested non-annotated package`() {
            val loggingDomain = loggingDomainOf(NonAnnotatedNestedPackageClass::class)
            loggingDomain.name shouldBe "OnPackage"
        }
    }
}
