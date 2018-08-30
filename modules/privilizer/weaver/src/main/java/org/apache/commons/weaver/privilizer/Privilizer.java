/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.weaver.privilizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * Coordinates privilization activities.
 */
public class Privilizer {
    /**
     * An ASM {@link ClassVisitor} for privilization.
     */
    abstract class PrivilizerClassVisitor extends ClassVisitor {
        String className;
        Type target;

        protected PrivilizerClassVisitor() {
            this(null);
        }

        protected PrivilizerClassVisitor(final ClassVisitor cv) { //NOPMD
            super(ASM_VERSION, cv);
        }

        protected Privilizer privilizer() {
            return Privilizer.this;
        }

        @Override
        @SuppressWarnings("PMD.UseVarargs") //overridden method
        public void visit(final int version, final int access, final String name, final String signature,
            final String superName, final String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
            target = Type.getObjectType(name);
        }
    }

    private final class CustomClassWriter extends ClassWriter {
        CustomClassWriter(final int flags) {
            super(flags);
        }

        CustomClassWriter(final ClassReader classReader, final int flags) {
            super(classReader, flags);
        }

        @Override
        protected ClassLoader getClassLoader() {
            return env.classLoader;
        }
    }

    /**
     * Convenient {@link ClassVisitor} layer to write classfiles into the {@link WeaveEnvironment}.
     */
    class WriteClass extends PrivilizerClassVisitor {

        WriteClass(final ClassReader classReader, final int flags) {
            super(new CustomClassWriter(classReader, flags));
        }

        WriteClass(final int flags) {
            super(new CustomClassWriter(flags));
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            final byte[] bytecode = ((ClassWriter) cv).toByteArray();

            if (verify) {
                verify(className, bytecode);
            }
            final WeaveEnvironment.Resource classfile = env.getClassfile(className);
            env.debug("Writing class %s to resource %s", className, classfile.getName());
            try (OutputStream outputStream = classfile.getOutputStream()) {
                outputStream.write(bytecode);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Privilizer weaver configuration prefix.
     */
    public static final String CONFIG_WEAVER = "privilizer.";

    /**
     * {@link AccessLevel} configuration key.
     * @see AccessLevel#parse(String)
     */
    public static final String CONFIG_ACCESS_LEVEL = CONFIG_WEAVER + "accessLevel";

    /**
     * Weave {@link Policy} configuration key.
     * @see Policy#parse(String)
     */
    public static final String CONFIG_POLICY = CONFIG_WEAVER + "policy";

    /**
     * Verification configuration key.
     * @see BooleanUtils#toBoolean(String)
     */
    public static final String CONFIG_VERIFY = CONFIG_WEAVER + "verify";

    private static final String GENERATE_NAME = "__privileged_%s";

    static final int ASM_VERSION = Opcodes.ASM6;
    static final Type[] EMPTY_TYPE_ARRAY = new Type[0];

    static Type wrap(final Type type) {
        switch (type.getSort()) {
        case Type.BOOLEAN:
            return Type.getType(Boolean.class);
        case Type.BYTE:
            return Type.getType(Byte.class);
        case Type.SHORT:
            return Type.getType(Short.class);
        case Type.INT:
            return Type.getType(Integer.class);
        case Type.CHAR:
            return Type.getType(Character.class);
        case Type.LONG:
            return Type.getType(Long.class);
        case Type.FLOAT:
            return Type.getType(Float.class);
        case Type.DOUBLE:
            return Type.getType(Double.class);
        case Type.VOID:
            return Type.getType(Void.class);
        default:
            return type;
        }
    }

    final WeaveEnvironment env;
    final AccessLevel accessLevel;
    final Policy policy;
    final boolean verify;

    /**
     * Create a new {@link Privilizer}.
     * @param env to use
     */
    public Privilizer(final WeaveEnvironment env) {
        super();
        this.env = env;
        this.policy = Policy.parse(env.config.getProperty(CONFIG_POLICY));
        this.accessLevel = AccessLevel.parse(env.config.getProperty(CONFIG_ACCESS_LEVEL));
        verify = BooleanUtils.toBoolean(env.config.getProperty(CONFIG_VERIFY));
    }

    String generateName(final String simple) {
        return String.format(GENERATE_NAME, simple);
    }

    void blueprint(final Class<?> type, final Privilizing privilizing) {
        final Object[] args = { type.getName(), privilizing };
        env.debug("blueprinting class %s %s", args);
        try (InputStream bytecode = env.getClassfile(type).getInputStream()) {
            final ClassReader classReader = new ClassReader(bytecode);

            ClassVisitor cvr;
            cvr = new WriteClass(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cvr = new PrivilizingVisitor(this, cvr);
            cvr = new BlueprintingVisitor(this, cvr, privilizing);

            classReader.accept(cvr, ClassReader.EXPAND_FRAMES);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void privilize(final Class<?> type) {
        final Object[] args = { type.getName() };
        env.debug("privilizing class %s", args);
        try (InputStream bytecode = env.getClassfile(type).getInputStream()) {
            final ClassReader classReader = new ClassReader(bytecode);
            ClassVisitor cv; // NOPMD
            cv = new WriteClass(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cv = new PrivilizingVisitor(this, cv);

            classReader.accept(cv, ClassReader.EXPAND_FRAMES);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void verify(final String className, final byte[] bytecode) {
        final ClassReader reader = new ClassReader(bytecode);

        env.debug("Verifying bytecode for class %s", className);
        final StringWriter w = new StringWriter(); //NOPMD
        CheckClassAdapter.verify(reader, env.classLoader, false, new PrintWriter(w));
        final String error = w.toString();
        if (!error.isEmpty()) {
            env.error(error);
            final StringWriter trace = new StringWriter();
            reader.accept(new TraceClassVisitor(new PrintWriter(trace)), ClassReader.SKIP_DEBUG);
            env.debug(trace.toString());
            throw new IllegalStateException();
        }
        Validate.validState(StringUtils.isBlank(error), error);

        final ClassVisitor checkInnerClasses = new ClassVisitor(ASM_VERSION, null) {
            final Set<String> innerNames = new HashSet<>();

            @Override
            public void visitInnerClass(final String name, final String outerName, final String innerName,
                final int access) {
                super.visitInnerClass(name, outerName, innerName, access);
                Validate.validState(innerNames.add(innerName), "%s already defined", innerName);
            }
        };
        reader.accept(checkInnerClasses, ClassReader.SKIP_CODE);
    }
}
