/*
 * Copyright 2022, TeamDev. All rights reserved.
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

package io.spine.internal.dependency

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer

/**
 * Dependencies on Spine modules.
 */
@Suppress("unused")
class Spine {

    private object EmptyExtensionAware: ExtensionAware {
        override fun getExtensions(): ExtensionContainer {
            TODO("Not yet implemented")
        }
    }

    @Suppress("ConvertSecondaryConstructorToPrimary")
    @Deprecated("Please use parameterless constructor.")
    constructor(ignored: ExtensionAware)

    /**
     * Versions for published Spine SDK artifacts.
     */
    object ArtifactVersion {

        /**
         * The version of ProtoData to be used in the project.
         * @see [ProtoData]
         */
        const val protoData = "0.8.0"

        /**
         * The version of `base` to use.
         * @see [Spine.base]
         */
        const val base = "2.0.0-SNAPSHOT.170"

        /**
         * The version of `core-java` to use.
         * @see [Spine.CoreJava.client]
         * @see [Spine.CoreJava.server]
         */
        const val core = "2.0.0-SNAPSHOT.141"

        /**
         * The version of `model-compiler` to use.
         * @see [Spine.modelCompiler]
         */
        const val mc = "2.0.0-SNAPSHOT.130"

        /**
         * The version of `mc-java` to use.
         */
        const val mcJava = "2.0.0-SNAPSHOT.132"

        /**
         * The version of `base-types` to use.
         * @see [Spine.baseTypes]
         */
        const val baseTypes = "2.0.0-SNAPSHOT.120"

        /**
         * The version of `time` to use.
         * @see [Spine.time]
         */
        const val time = "2.0.0-SNAPSHOT.121"

        /**
         * The version of `change` to use.
         * @see [Spine.change]
         */
        const val change = "2.0.0-SNAPSHOT.118"

        /**
         * The version of `text` to use.
         *
         * @see Spine.text
         */
        const val text = "2.0.0-SNAPSHOT.3"

        /**
         * The version of `tool-base` to use.
         * @see [Spine.toolBase]
         */
        const val toolBase = "2.0.0-SNAPSHOT.156"

        /**
         * The version of `validation` to use.
         * @see [Spine.validation]
         */
        const val validation = "2.0.0-SNAPSHOT.81"

        /**
         * The version of Javadoc Tools to use.
         * @see [Spine.javadocTools]
         */
        const val javadocTools = "2.0.0-SNAPSHOT.75"
    }

    companion object {
        const val group = "io.spine"
        const val toolsGroup = "io.spine.tools"

        /**
         * The version of ProtoData to be used in the project.
         *
         * We do it here instead of `versions.gradle.kts` because we later use
         * it in a `plugins` section in a build script.
         *
         * @see [ProtoData]
         */
        const val protoDataVersion = ArtifactVersion.protoData
    }

    val base = "$group:spine-base:${ArtifactVersion.base}"
    val logging = "$group:spine-logging:${ArtifactVersion.base}"
    val reflect = "$group:spine-reflect:${ArtifactVersion.base}"
    val baseTypes = "$group:spine-base-types:${ArtifactVersion.baseTypes}"
    val time = "$group:spine-time:${ArtifactVersion.time}"
    val change = "$group:spine-change:${ArtifactVersion.change}"
    val text = "$group:spine-text:${ArtifactVersion.text}"

    val testlib = "$toolsGroup:spine-testlib:${ArtifactVersion.base}"
    val testUtilTime = "$toolsGroup:spine-testutil-time:${ArtifactVersion.time}"
    val toolBase = "$toolsGroup:spine-tool-base:${ArtifactVersion.toolBase}"
    val pluginBase = "$toolsGroup:spine-plugin-base:${ArtifactVersion.toolBase}"
    val pluginTestlib = "$toolsGroup:spine-plugin-testlib:${ArtifactVersion.toolBase}"
    val modelCompiler = "$toolsGroup:spine-model-compiler:${ArtifactVersion.mc}"

    object McJava {
        const val version = ArtifactVersion.mcJava
        const val pluginId = "io.spine.mc-java"
        const val pluginLib = "$toolsGroup:spine-mc-java-plugins:${version}:all"
    }

    /**
     *  Does not allow re-definition via a project property.
     *  Please change [ArtifactVersion.javadocTools].
     */
    val javadocTools = "$toolsGroup::${ArtifactVersion.javadocTools}"

    @Deprecated("Please use `validation.runtime`", replaceWith = ReplaceWith("validation.runtime"))
    val validate = "$group:spine-validate:${ArtifactVersion.base}"

    val validation = Validation
    val coreJava = CoreJava
    val client = coreJava.client // Added for brevity.
    val server = coreJava.server // Added for brevity.

    /**
     * Dependencies on Spine validation modules.
     *
     * See [`SpineEventEngine/validation`](https://github.com/SpineEventEngine/validation/).
     */
    object Validation {
        const val group = "io.spine.validation"
        const val runtime = "$group:spine-validation-java-runtime:${ArtifactVersion.validation}"
        const val java = "$group:spine-validation-java:${ArtifactVersion.validation}"
        const val model = "$group:spine-validation-model:${ArtifactVersion.validation}"
        const val config = "$group:spine-validation-configuration:${ArtifactVersion.validation}"
    }

    /**
     * Dependencies on ProtoData modules.
     *
     * See [`SpineEventEngine/ProtoData`](https://github.com/SpineEventEngine/ProtoData/).
     */
    object ProtoData {
        const val group = "io.spine.protodata"
        const val version = protoDataVersion
        const val compiler = "$group:protodata-compiler:$version"

        const val codegenJava = "io.spine.protodata:protodata-codegen-java:$version"

        const val pluginId = "io.spine.protodata"
        const val pluginLib = "${Spine.group}:protodata:$version"
    }

    /**
     * Dependencies on `core-java` modules.
     *
     * See [`SpineEventEngine/core-java`](https://github.com/SpineEventEngine/core-java/).
     */
    object CoreJava {
        val core = "$group:spine-core:${ArtifactVersion.core}"
        val client = "$group:spine-client:${ArtifactVersion.core}"
        val server = "$group:spine-server:${ArtifactVersion.core}"
        val testUtilServer = "$toolsGroup:spine-testutil-server:${ArtifactVersion.core}"
    }
}
