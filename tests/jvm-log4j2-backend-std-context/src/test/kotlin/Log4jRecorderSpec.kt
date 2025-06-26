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

import given.map.CustomLoggingLevel.ANNOUNCEMENT
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.WARNING
import io.spine.logging.compareTo
import io.spine.logging.testing.toLevel
import io.spine.logging.testing.toLog4j2

internal class Log4jRecorderSpec: ShouldSpec({

    val anno = ANNOUNCEMENT.toLog4j2()
    val warn = WARNING.toLog4j2()
    val info = INFO.toLog4j2()

    should("use levels converted to Log4j") {
        // In terms of Log4J lesser level means higher barrier for log records.
        // Therefore, the custom level of `ANNOUNCEMENT` defined in JUL terms as being
        // higher than `WARNING` should be less than `INFO` and `WARNING` when
        // converted to Log4J.
        (anno <= info) shouldBe true
        (anno <= warn) shouldBe true
    }

    should("convert Log4j level to level") {
        val annoBack = anno.toLevel()
        val warnBack = warn.toLevel()
        (annoBack > warnBack) shouldBe true
        (annoBack > warnBack) shouldBe true
    }
})
