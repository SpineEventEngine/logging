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

package io.spine.logging.context

import io.spine.logging.jvm.backend.LogData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * This file contains Kotest-like assertions for [LogData].
 */

/**
 * Asserts that this [LogData] has a given [value] as a literal
 * or template message.
 *
 * The message is literal when it is passed to the logger without
 * any formatting arguments, otherwise it is part of [LogData.templateContext].
 */
internal infix fun LogData.shouldHaveMessage(value: String?) {
    if (templateContext != null) {
        templateContext!!.message shouldBe value
    } else {
        literalArgument shouldBe value
    }
}

/**
 * Asserts that this [LogData] has given [args], which were passed
 * for message formatting.
 *
 * This method will NOT fail if the passed [args] is empty as long as
 * this [LogData] doesn't have any arguments too.
 */
internal fun LogData.shouldHaveArguments(vararg args: Any?) {
    if (templateContext == null && args.isEmpty()) {
        return
    } else {
        arguments.shouldContainExactly(*args)
    }
}
