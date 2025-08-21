/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm

import com.google.errorprone.annotations.RestrictedApi
import io.spine.annotation.Internal
import io.spine.logging.LogSite

/**
 * Creates a log site injected from constants held in a class' constant pool.
 *
 * Used for compile-time log site injection, and by the agent.
 *
 * This is a non-deprecated replacement for the legacy JvmLogSite.injectedLogSite shim.
 */
@Internal
@RestrictedApi(
    explanation =
        "This method is only used for log-site injection and should not be called directly.",
    allowlistAnnotations = [LogSiteInjector::class]
)
public fun injectedLogSite(
    internalClassName: String,
    methodName: String,
    encodedLineNumber: Int,
    sourceFileName: String?
): LogSite = InjectedJvmLogSite(
    internalClassName,
    methodName,
    encodedLineNumber,
    sourceFileName
)
