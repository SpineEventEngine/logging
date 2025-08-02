/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

@file:JvmName("StringBuilders")

package io.spine.logging.jvm.backend

import io.spine.logging.jvm.JvmLogSite
import java.io.IOException
import java.math.BigInteger
import java.util.*
import java.util.FormattableFlags.ALTERNATE
import java.util.FormattableFlags.LEFT_JUSTIFY
import java.util.FormattableFlags.UPPERCASE

/**
 * Extension functions for [StringBuilder] related to log message formatting.
 */

/**
 * Appends log-site information in the default format, including a trailing space.
 *
 * @param logSite The log site to be appended (ignored if [JvmLogSite.invalid]).
 * @return whether the log-site was appended.
 */
public fun StringBuilder.appendLogSite(logSite: JvmLogSite): Boolean {
    if (logSite == JvmLogSite.invalid) {
        return false
    }
    this.append(logSite.className)
        .append('.')
        .append(logSite.methodName)
        .append(':')
        .append(logSite.lineNumber)
    return true
}
