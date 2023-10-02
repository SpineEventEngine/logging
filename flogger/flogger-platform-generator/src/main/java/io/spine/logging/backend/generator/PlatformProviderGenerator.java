/*
 * Copyright 2018, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.generator;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static java.nio.file.Files.newOutputStream;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.F_SAME1;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

/**
 * Generates {@code PlatformProvider} class from scratch.
 *
 * <p>The generated class discovers {@code io.spine.logging.flogger.backend.Platform}
 * implementations, and creates their instances.
 *
 * <p>This generator is necessary to create a class, which explicitly
 * references the actual platform implementation classes without having
 * them visible as a build dependency.
 *
 * <p>If the code were compiled with {@code javac} that wouldn't work,
 * because {@code javac} needs to observe the classes on its classpath.
 * By resorting to manually generating file, we can work around the limitation
 * and avoid the dependency on platform implementations while still keeping
 * an explicit reference to the classes.
 *
 * <p>The advantage of this approach is that tools that operate on bytecode
 * (e.g., proguard) observe the dependency correctly, which is not the case
 * when reflection is used to look up classes.
 *
 * @see <a href="http://rb.gy/mjs13">Original Java code of Google Flogger</a>
 */
@SuppressWarnings("DuplicateStringLiteralInspection") // Uses literals with FQN.
public final class PlatformProviderGenerator {

    private static final String[] PLATFORM_CLASSES = {
            "Lio/spine/logging/flogger/backend/system/DefaultPlatform;",
    };

    /**
     * Prevents instantiation of this utility class.
     */
    private PlatformProviderGenerator() {
    }

    public static void main(String[] args) throws IOException {

        // Create the class.
        var classWriter = new ClassWriter(0);
        classWriter.visit(
                V1_6,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                "io/spine/logging/flogger/backend/PlatformProvider",
                null,
                "java/lang/Object",
                null)
        ;

        // Create the no-op constructor.
        var methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();

        // Create the static getter method.
        methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC + ACC_STATIC,
                "getPlatform",
                "()Lio/spine/logging/flogger/backend/Platform;",
                null,
                null
        );

        // Try different platforms.
        for (var platformClass : PLATFORM_CLASSES) {
            tryBlockForPlatform(methodVisitor, platformClass);
        }

        // Return `null` if no platform is found.
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();

        // Finish creating the class.
        classWriter.visitEnd();

        // Write the class to the output file.
        var path = Paths.get(args[0]);
        Files.createDirectories(path.getParent());
        try (var jar = new JarOutputStream(newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            var entry = new ZipEntry("io/spine/logging/flogger/backend/PlatformProvider.class");
            // Clear timestamp to ensure JAR is deterministic for build cache.
            entry.setTime(0);
            jar.putNextEntry(entry);
            jar.write(classWriter.toByteArray());
            jar.closeEntry();
        }
    }

    /**
     * Generates the enveloping try-catch block.
     *
     * <p>Here is an example of the generated code:
     *
     * <pre>
     * try {
     *     return (Platform) PlatformClass.class.getDeclaredConstructor().newInstance();
     * } catch(NoClassDefFoundError | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
     *     return null;
     * }
     * </pre>
     *
     * <p>Please note that the exception types need to be listed explicitly
     * rather than using {@link ReflectiveOperationException}) because
     * that parent exception type is not available on Android until
     * API level 19 (Android 4.4, 2013).
     */
    private static void tryBlockForPlatform(MethodVisitor methodVisitor, String platformType) {
        methodVisitor.visitCode();

        /*
        Generate the enveloping try-catch block:

        ```
        try {
          ...
        } catch (NoClassDefFoundError | IllegalAccessException | InstantiationException
            | InvocationTargetException | NoSuchMethodException e) {
          ...
        }
        ```

         */
        var startLabel = new Label();
        var endLabel = new Label();
        var handlerLabel = new Label();
        methodVisitor.visitTryCatchBlock(
                startLabel, endLabel, handlerLabel, "java/lang/NoClassDefFoundError");
        methodVisitor.visitTryCatchBlock(
                startLabel, endLabel, handlerLabel, "java/lang/IllegalAccessException");
        methodVisitor.visitTryCatchBlock(
                startLabel, endLabel, handlerLabel, "java/lang/InstantiationException");
        methodVisitor.visitTryCatchBlock(
                startLabel, endLabel, handlerLabel, "java/lang/reflect/InvocationTargetException");
        methodVisitor.visitTryCatchBlock(
                startLabel, endLabel, handlerLabel, "java/lang/NoSuchMethodException");
        methodVisitor.visitLabel(startLabel);



        /*
        Generate the actual reflective constructor call inside the try block:

        ```
        return (Platform) PlatformClass.class.getDeclaredConstructor().newInstance();
        ```

        Note that the constructor call happens reflectively to make sure
        that the platform class isn't loaded until actually executing
        this instruction.

        That is important because an earlier class load could happen outside
        the try/catch block where we are explicitly handling the case of
        the class not being present.
         */
        methodVisitor.visitLdcInsn(Type.getType(platformType));
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Class",
                "getDeclaredConstructor",
                "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;",
                false);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/reflect/Constructor",
                "newInstance",
                "([Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        methodVisitor.visitTypeInsn(CHECKCAST, "io/spine/logging/flogger/backend/Platform");
        methodVisitor.visitLabel(endLabel);
        methodVisitor.visitInsn(ARETURN);

        /*
        Generate the catch block of the overall try/catch.

        The catch block is actually just empty, but Java does require the catch
        handler to have at least a frame in it to declare the exception variable
        that is available within the catch block scope.

        https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-3.html#jvms-3.12
         */
        methodVisitor.visitLabel(handlerLabel);
        methodVisitor.visitFrame(F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        methodVisitor.visitVarInsn(ASTORE, 0);
    }
}
