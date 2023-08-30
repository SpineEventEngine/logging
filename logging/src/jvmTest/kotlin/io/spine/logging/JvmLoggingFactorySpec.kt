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

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.LoggingFactory.loggingDomainOf
import io.spine.logging.dynamic.backend.captureLogData
import io.spine.logging.given.EnclosingClass
import io.spine.logging.given.EnclosingClassA
import io.spine.logging.given.EnclosingClassB
import io.spine.logging.given.EnclosingCompanionObject
import io.spine.logging.given.EnclosingDataClass
import io.spine.logging.given.EnclosingEnumClass
import io.spine.logging.given.EnclosingObject
import io.spine.logging.given.EnclosingSealedClass
import io.spine.logging.given.SealedInheritingChild
import io.spine.logging.given.SealedOverridingChild
import io.spine.logging.given.domain.AnnotatedClass
import io.spine.logging.given.domain.IndirectlyAnnotatedClass
import io.spine.logging.given.domain.nested.NonAnnotatedNestedPackageClass
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`JvmLoggingFactory` should")
internal class JvmLoggingFactorySpec {

    @Nested inner class
    `create a logger for the enclosing` {

        @Test
        fun `class`() {
            val logged = captureLogData {
                EnclosingClass().logger.atInfo().log()
            }
            logged shouldHaveSize 1
            logged.first().loggerName shouldBe EnclosingClass::class.qualifiedName
        }

        @Test
        fun `sealed class`() {
            val logged = captureLogData {
                SealedInheritingChild().logger.atInfo().log()
                SealedOverridingChild().logger.atInfo().log()
            }
            logged shouldHaveSize 2
            logged[0].loggerName shouldBe EnclosingSealedClass::class.qualifiedName
            logged[1].loggerName shouldBe SealedOverridingChild::class.qualifiedName
        }

        @Test
        fun `data class`() {
            val logged = captureLogData {
                EnclosingDataClass().logger.atInfo().log()
            }
            logged shouldHaveSize 1
            logged.first().loggerName shouldBe EnclosingDataClass::class.qualifiedName
        }

        @Test
        fun `enum class`() {
            val logged = captureLogData {
                EnclosingEnumClass.ONE.logger.atInfo().log()
                EnclosingEnumClass.TWO.logger.atInfo().log()
                EnclosingEnumClass.THREE.logger.atInfo().log()
            }
            logged shouldHaveSize 3
            logged.map { it.loggerName }.distinct() shouldHaveSize 1
            logged.first().loggerName shouldBe EnclosingEnumClass::class.qualifiedName
        }

        @Test
        fun `local class`() {
            class EnclosingLocalClass {
                val logger = LoggingFactory.forEnclosingClass()
            }

            val logged = captureLogData {
                EnclosingLocalClass().logger.atInfo().log()
            }

            logged shouldHaveSize 1
            // A local class doesn't have a qualified name.
            logged.first().loggerName shouldBe EnclosingLocalClass::class.jvmName
        }

        @Test
        fun `object`() {
            val logged = captureLogData {
                EnclosingObject.logger.atInfo().log()
            }
            logged shouldHaveSize 1
            logged.first().loggerName shouldBe EnclosingObject::class.qualifiedName
        }

        @Test
        fun `companion object`() {
            val logged = captureLogData {
                EnclosingCompanionObject.logger.atInfo().log()
            }
            logged shouldHaveSize 1
            logged.first().loggerName shouldBe EnclosingCompanionObject::class.qualifiedName
        }

        @Test
        fun `anonymous object`() {
            var anonymousClass: KClass<*>? = null // Will be used for assertion.
            val logged = captureLogData {
                val anonymous = object {
                    val logger = LoggingFactory.forEnclosingClass()
                }
                anonymousClass = anonymous::class
                anonymous.logger.atInfo().log()
            }
            logged shouldHaveSize 1
            // An anonymous object doesn't have a qualified name.
            logged.first().loggerName shouldBe anonymousClass!!.jvmName
        }
    }

    @Test
    fun `provide the same logger for the same enclosing class`() {
        val firstLogger = LoggingFactory.forEnclosingClass()
        val secondLogger = LoggingFactory.forEnclosingClass()
        firstLogger shouldBeSameInstanceAs secondLogger
        firstLogger shouldBe secondLogger
    }

    @Test
    fun `provide different loggers for different classes`() {
        val loggerA = EnclosingClassA().logger
        val loggerB = EnclosingClassB().logger
        loggerA shouldBeSameInstanceAs loggerB
        loggerA shouldNotBe loggerB
    }

    @Nested inner class
    `obtain a logging domain for` {

        @Test
        fun `directly annotated class`() {
            val loggingDomain = loggingDomainOf(AnnotatedClass::class)
            loggingDomain.name shouldBe "OnClass"
        }

        /**
         * This test may fail due to [#39](https://github.com/SpineEventEngine/logging/issues/39).
         */
        @Test
        fun `a class with annotated package`() {
            val loggingDomain = loggingDomainOf(IndirectlyAnnotatedClass::class)
            loggingDomain.name shouldBe "OnPackage"
        }

        /**
         * This test may fail due to [#39](https://github.com/SpineEventEngine/logging/issues/39).
         */
        @Test
        fun `a class in a nested non-annotated package`() {
            val loggingDomain = loggingDomainOf(NonAnnotatedNestedPackageClass::class)
            loggingDomain.name shouldBe "OnPackage"
        }
    }
}
