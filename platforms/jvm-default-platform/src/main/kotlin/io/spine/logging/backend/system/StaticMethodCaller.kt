/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.backend.system

/**
 * Helper to call a no-arg constructor or static getter to obtain an instance of
 * a specified type. This is used for logging platform "plugins". It is expected
 * that these constructors/methods will be invoked once during logger
 * initialization and then the results cached in the platform class.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/util/StaticMethodCaller.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
internal object StaticMethodCaller {

    // TODO(cgdecker): Rename this class; eventually perhaps just roll it into DefaultPlatform

    private const val GET_INSTANCE = "getInstance"

    @JvmStatic
    fun <T> getInstanceFromSystemProperty(propertyName: String, type: Class<T>): T? =
        getInstanceFromSystemProperty(propertyName, null, type)

    @JvmStatic
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun <T> getInstanceFromSystemProperty(
        propertyName: String,
        defaultValue: String?,
        type: Class<T>
    ): T? {
        val property = readProperty(propertyName, defaultValue) ?: return null

        val hashIndex = property.indexOf('#')
        val className = if (hashIndex == -1) property else property.substring(0, hashIndex)
        // TODO(cgdecker): Eventually we should eliminate method checks and only use constructors
        val methodName = if (hashIndex == -1) GET_INSTANCE else property.substring(hashIndex + 1)

        var attemptedMethod = "$className#$methodName()"
        return try {
            val clazz = Class.forName(className)
            try {
                val method = clazz.getMethod(methodName)
                return type.cast(method.invoke(null))
            } catch (e: NoSuchMethodException) {
                if (hashIndex == -1 || methodName != GET_INSTANCE) {
                    error("method '%s' does not exist: %s\n", property, e)
                    return null
                }
            }
            attemptedMethod = "new $className()"
            type.cast(clazz.getConstructor().newInstance())
        } catch (_: ClassNotFoundException) {
            null
        } catch (e: ClassCastException) {
            error(
                "cannot cast result of calling '%s' to '%s': %s\n",
                attemptedMethod,
                type.name,
                e
            )
            null
        } catch (e: Exception) {
            error(
                "cannot call expected no-argument constructor or static method '%s': %s\n",
                attemptedMethod,
                e
            )
            null
        }
    }
}

private fun readProperty(propertyName: String, defaultValue: String?): String? {
    return try {
        System.getProperty(propertyName, defaultValue)
    } catch (e: SecurityException) {
        error("Cannot read property name %s: %s", propertyName, e)
        null
    }
}

// This cannot use a fluent logger here, and it is even risky to use a JDK logger.
private fun error(msg: String, vararg args: Any?) {
    val formattedMsg = if (args.isEmpty()) msg else msg.format(*args)
    System.err.println("${StaticMethodCaller::class.java}: $formattedMsg")
}
