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

package io.spine.logging

import io.spine.logging.backend.Platform
import io.spine.logging.jvm.Middleman
import io.spine.reflect.CallerFinder
import kotlin.reflect.KClass

/**
 * Obtains a [JvmLogger] for a given class.
 */
public actual object LoggingFactory: ClassValue<JvmLogger>() {

    @JvmStatic
    @JvmName("getLogger") // Set the name explicitly to avoid the synthetic `$logging` suffix.
    public actual fun <API: LoggingApi<API>> loggerFor(cls: KClass<*>): Logger<API> {
        @Suppress("UNCHECKED_CAST") // Safe as `JvmLogger.Api`
        val result = get(cls.java) as Logger<API>
        return result
    }

    override fun computeValue(cls: Class<*>): JvmLogger =
        createForClass(cls)

    @JvmStatic
    public actual fun loggingDomainOf(cls: KClass<*>): LoggingDomain =
        LoggingDomainClassValue.get(cls)

    @JvmStatic
    public actual fun <T : Any> singleMetadataKey(
        label: String,
        valueClass: KClass<T>
    ): MetadataKey<T> = MetadataKey.single(label, valueClass)

    @JvmStatic
    public fun <T: Any> singleMetadataKey(label: String, type: Class<T>): MetadataKey<T> =
        singleMetadataKey(label, type.kotlin)

    @JvmStatic
    public actual fun <T : Any> repeatedMetadataKey(
        label: String,
        valueClass: KClass<T>
    ): MetadataKey<T> = MetadataKey.repeated(label, valueClass)

    @JvmStatic
    public fun <T : Any> repeatedMetadataKey(label: String, type: Class<T>): MetadataKey<T> =
        repeatedMetadataKey(label, type.kotlin)

    private fun createForClass(cls: Class<*>): JvmLogger {
        val floggerBackend = Platform.getBackend(cls.name)
        val flogger = Middleman(floggerBackend)
        // As for now, `JvmLogger` just delegates actual work to Flogger.
        return JvmLogger(cls.kotlin, flogger)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST") // `JvmLogger` is casted to `Logger<API>`.
    public actual fun <API : LoggingApi<API>> forEnclosingClass(): Logger<API> {
        val factoryClass = LoggingFactory::class.java
        val callerStackElement = CallerFinder.findCallerOf(factoryClass, 0)
        val callerClassName = callerStackElement!!.className
        val callerClass = Class.forName(callerClassName)
        val result = get(callerClass) as Logger<API>
        return result
    }
}
