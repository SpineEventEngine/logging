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

package io.spine.logging.given

import io.spine.logging.Logger
import io.spine.logging.LoggingFactory

/**
 * This file contains different class and object declarations
 * for which a logging factory can provide a logger.
 *
 * @see [LoggingFactory.forEnclosingClass]
 */

internal class EnclosingClass {
    val logger = LoggingFactory.forEnclosingClass()
}

internal class EnclosingClassA {
    val logger = LoggingFactory.forEnclosingClass()
}

internal class EnclosingClassB {
    val logger = LoggingFactory.forEnclosingClass()
}

internal sealed class EnclosingSealedClass {
    open val logger = LoggingFactory.forEnclosingClass()
}

internal class SealedInheritingChild : EnclosingSealedClass()

internal class SealedOverridingChild : EnclosingSealedClass() {
    override val logger = LoggingFactory.forEnclosingClass()
}

internal data class EnclosingDataClass(
    val logger: Logger = LoggingFactory.forEnclosingClass()
)

@Suppress("unused")
internal enum class EnclosingEnumClass {

    ONE, TWO, THREE;

    val logger = LoggingFactory.forEnclosingClass()
}

internal object EnclosingObject {
    val logger = LoggingFactory.forEnclosingClass()
}

internal class EnclosingCompanionObject {
    companion object {
        val logger = LoggingFactory.forEnclosingClass()
    }
}
