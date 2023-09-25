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

package com.google.common.flogger.grpc

import com.google.common.flogger.testing.AbstractContextDataProviderSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.logging.flogger.context.ContextDataProvider
import java.util.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [GrpcContextDataProvider].
 *
 * @see <a href="https://github.com/google/flogger/blob/70c5aea863952ee61b3d33afb41f2841b6d63455/grpc/src/test/java/com/google/common/flogger/grpc/GrpcContextDataProviderTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`GrpcContextDataProvider` should")
internal class GrpcContextDataProviderSpec : AbstractContextDataProviderSpec() {

    override val implementationUnderTest: ContextDataProvider =
        GrpcContextDataProvider.getInstance()

    @Test
    fun `be able to be loaded as a Java service`() {
        val serviceLoader = ServiceLoader.load(ContextDataProvider::class.java)
        val optionalContextDataProvider = serviceLoader.findFirst()
        optionalContextDataProvider.shouldBePresent()

        val contextDataProvider = optionalContextDataProvider.get()
        contextDataProvider.shouldNotBeNull()
        contextDataProvider.shouldBeInstanceOf<GrpcContextDataProvider>()
    }
}
