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

import given.map.L1Direct
import given.map.nested.L2Direct
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.logging.Level
import io.spine.logging.WithLogging
import io.spine.logging.context.LogLevelMap
import io.spine.logging.toJavaLogging
import io.spine.testing.logging.context.AbstractLogLevelMapSpec
import io.spine.testing.logging.jul.checkLogging

internal class LogLevelMapSpec: AbstractLogLevelMapSpec() {

    override fun configureBuilder(builder: LogLevelMap.Builder) {
        with(builder) {
            setDefault(defaultLevel)
            add(level1, L1Direct::class)
            add(level2Direct, L2Direct::class)
            //add(level2Package, "given.nested")
        }
    }

    override fun checkSettingDefaultLevel() {
        val fixture = DefLoggingFixture()
        checkLogging(fixture.javaClass, Level.ALL) {
            fixture.logError()
            records shouldHaveSize 1

            val record = records[0]
            record.level shouldBe Level.ERROR.toJavaLogging()
        }
    }

    init {
        should("allow default level being set") {
            checkSettingDefaultLevel()
        }
    }
    
    companion object {
        val defaultLevel = Level.ERROR
        val level1 = Level.DEBUG
        val level2Direct = Level.WARNING
        val level2Package = Level.INFO
    }
}

private class DefLoggingFixture: WithLogging {
    fun logError() {
        val level = LogLevelMapSpec.defaultLevel
        logger.at(level).log { "Message at level `$level`." }
    }
}
