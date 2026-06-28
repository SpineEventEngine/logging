/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.logging.backend.otel.bootstrap

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import java.net.ServerSocket
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`OtelLogging` should")
internal class OtelLoggingSpec {

    @Test
    fun `resolve the default endpoint when the environment variable is unset`() {
        // `OTEL_EXPORTER_OTLP_ENDPOINT` is not set in the test JVM, so resolution
        // falls back to the default. This is pure logic: no exporter is built and no
        // port is touched.
        OtelLogging.endpointFromEnvironment() shouldBe OtelLogging.DEFAULT_OTLP_HTTP_ENDPOINT
    }

    @Test
    fun `build, install and shut down an OTLP HTTP pipeline`() {
        shouldNotThrowAny {
            // Aim the exporter at a free local port rather than the well-known 4318:
            // the test must not collide with — or accidentally export into — a real
            // collector or a parallel suite on the standard port. The batch processor
            // exports asynchronously, so construction, installation and shutdown all
            // succeed whether or not anything is listening.
            val installed = OtelLogging.installOtlpHttp("http://localhost:${freePort()}")
            installed.close()
        }
    }

    /** Returns a currently-free local TCP port. */
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
