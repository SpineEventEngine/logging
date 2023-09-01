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

package io.spine.logging

import io.spine.logging.LoggingDomain.Companion.noOp
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Obtains a [LoggingDomain] for a Java or Kotlin class.
 *
 * The annotation is looked as:
 *   1. An annotation of the type [LoggingDomain] of the given class or
 *      an annotation of enclosing classes from innermost to outermost.
 *   2. The same as 1. but for [JvmLoggingDomain] annotation.
 *   3. As a package annotation (of the type [JvmLoggingDomain]) for the package
 *      of the given class, or "parent" packages from innermost to outermost.
 *
 * When [JvmLoggingDomain] is found it is converted to [LoggingDomain] instance.
 */
internal object LoggingDomainClassValue: ClassValue<LoggingDomain>() {

    private val jvmLoggingDomains = AnnotationsLookup(JvmLoggingDomain::class)

    internal fun get(cls: KClass<*>) = get(cls.java)

    @Suppress("ReturnCount") // to ease the flow on null results
    override fun computeValue(javaClass: Class<*>): LoggingDomain {

        with(javaClass) {
            findWithNesting<LoggingDomain>()?.let {
                return it
            }
            findWithNesting<JvmLoggingDomain>()?.let {
                return it.toLoggingDomain()
            }
        }

        val classPackage = javaClass.`package`
        val jvmLoggingDomain = jvmLoggingDomains.getFor(classPackage)
        return jvmLoggingDomain?.toLoggingDomain() ?: noOp
    }
}

/**
 * Attempts to find the annotation of type [T] in this [KClass] or enclosing classes.
 */
@Suppress("ReturnCount") // to ease the flow on null results
private inline fun <reified T: Annotation> Class<*>.findWithNesting(): T? {

    // `kotlin.reflect.findAnnotation()` throws an error on an attempt
    // to be called upon Java anonymous class. Anyway, the domain annotation
    // can't be applied to local members.
    if (!isAnonymousClass) {
        kotlin.findAnnotation<T>()?.let {
            return it
        }
    }

    var enclosingClass = this.enclosingClass
    while (enclosingClass != null) {
        enclosingClass.kotlin.findAnnotation<T>()?.let {
            return it
        }
        enclosingClass = enclosingClass.enclosingClass
    }

    return null
}

/**
 * Converts this [JvmLoggingDomain] instance to [LoggingDomain] instance.
 */
private fun JvmLoggingDomain.toLoggingDomain(): LoggingDomain = LoggingDomain(value)
