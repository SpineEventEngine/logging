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

package io.spine.logging.context

import io.spine.logging.Level
import io.spine.logging.toJavaLogging
import io.spine.logging.toLevel
import kotlin.reflect.KClass
import com.google.common.flogger.context.LogLevelMap as FLogLevelMap
import com.google.common.flogger.context.LogLevelMap.Builder as FBuilder

internal actual object LoggingContextFactory {

    actual fun levelMapBuilder(): LogLevelMap.Builder =
        BuilderImpl()

    actual fun levelMap(map: Map<String, Level>, defaultLevel: Level): LogLevelMap =
        MapImpl(map, defaultLevel)
}

private class BuilderImpl : LogLevelMap.Builder {

    private val delegate: FBuilder = FLogLevelMap.builder()

    override fun add(level: Level, vararg classes: KClass<*>): LogLevelMap.Builder {
        classes.forEach { cls ->
            delegate.add(level.toJavaLogging(), cls.java)
        }
        return this
    }

    override fun add(level: Level, vararg packageNames: String): LogLevelMap.Builder {
        packageNames.forEach {
            val javaPackage = packageNamed(it)
            delegate.add(level.toJavaLogging(), javaPackage)
        }
        return this
    }

    override fun setDefault(level: Level): LogLevelMap.Builder {
        delegate.setDefault(level.toJavaLogging())
        return this
    }

    override fun build(): LogLevelMap {
        return MapImpl(delegate.build())
    }
}

private fun packageNamed(name: String): Package {
    val classloader = ClassLoader.getPlatformClassLoader()
    classloader.getDefinedPackage(name)?.let {
        return it
    }
    val javaLang = String::class.java.`package`
    if (name == javaLang.name) {
        return javaLang
    }
    error("Unable to obtain a package named `$name`.")
}

private class MapImpl(private val delegate: FLogLevelMap): LogLevelMap {

    constructor(map: Map<String, Level>, defaultLevel: Level) :
            this(createDelegate(map, defaultLevel))

    override fun levelOf(loggerName: String): Level =
        delegate.getLevel(loggerName).toLevel()

    override fun merge(other: LogLevelMap): LogLevelMap {
        // It is safe to downcast because there's only one implementation per platform.
        // That is, all the instances implementing `LogLevelMap` would be `MapImpl`.
        val otherDelegate = (other as MapImpl).delegate
        val merged = delegate.merge(otherDelegate)
        return MapImpl(merged)
    }

    private companion object {
        fun createDelegate(map: Map<String, Level>, defaultLevel: Level): FLogLevelMap {
            val convertedMap = map.mapValues { it.value.toJavaLogging() }
            return FLogLevelMap.create(convertedMap, defaultLevel.toJavaLogging())
        }
    }
}
