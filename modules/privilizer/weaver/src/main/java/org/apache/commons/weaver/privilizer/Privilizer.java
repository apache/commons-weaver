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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import javax.activation.DataSource;

import org.apache.commons.io.IOUtils;
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

public class Privilizer {
    abstract class PrivilizerClassVisitor extends ClassVisitor {
        String className;
        Type target;

        protected PrivilizerClassVisitor() {
            this(null);
        }

        protected PrivilizerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        protected Privilizer privilizer() {
            return Privilizer.this;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
            target = Type.getObjectType(name);
        }
    }

    class WriteClass extends PrivilizerClassVisitor {
        WriteClass(ClassWriter cw) {
            super(cw);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            final byte[] bytecode = ((ClassWriter) cv).toByteArray();

            if (verify) {
                verify(className, bytecode);
            }

            final String resourceName = className.replace('.', File.separatorChar) + ".class";
            final DataSource classfile = env.getResource(resourceName);
            env.debug("Writing class %s to resource %s", className, resourceName);
            OutputStream outputStream = null;
            try {
                outputStream = classfile.getOutputStream();
                IOUtils.write(bytecode, outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(outputStream);
            }
        }
    }

    public static final String CONFIG_WEAVER = "privilizer.";

    public static final String CONFIG_ACCESS_LEVEL = CONFIG_WEAVER + "accessLevel";

    public static final String CONFIG_POLICY = CONFIG_WEAVER + "policy";

    public static final String CONFIG_VERIFY = CONFIG_WEAVER + "verify";

    private static final String GENERATE_NAME = "__privileged_%s";

    static final Type[] EMPTY_TYPE_ARRAY = new Type[0];

    final WeaveEnvironment env;
    final AccessLevel accessLevel;
    final Policy policy;
    final boolean verify;

    public Privilizer(WeaveEnvironment env) {
        super();
        this.env = env;
        this.policy = Policy.parse(env.config.getProperty(CONFIG_POLICY));
        this.accessLevel = AccessLevel.parse(env.config.getProperty(CONFIG_ACCESS_LEVEL));
        verify = BooleanUtils.toBoolean(env.config.getProperty(CONFIG_VERIFY));
    }

    String generateName(String simple) {
        return String.format(GENERATE_NAME, simple);
    }

    Type wrap(Type t) {
        switch (t.getSort()) {
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
            return t;
        }
    }

    void blueprint(final Class<?> type, final Privilizing privilizing) {
        Object[] args = { type.getName(), privilizing };
        env.debug("blueprinting class %s %s", args);
        InputStream bytecode = null;
        try {
            bytecode = env.getClassfile(type).getInputStream();
            final ClassReader classReader = new ClassReader(bytecode);

            ClassVisitor cv;
            cv = new WriteClass(new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
            cv = new PrivilizingVisitor(this, cv);
            cv = new BlueprintingVisitor(this, cv, privilizing);

            classReader.accept(cv, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(bytecode);
        }
    }

    void privilize(final Class<?> type) {
        Object[] args = { type.getName() };
        env.debug("privilizing class %s", args);
        InputStream bytecode = null;
        try {
            bytecode = env.getClassfile(type).getInputStream();
            final ClassReader classReader = new ClassReader(bytecode);
            ClassVisitor cv;
            cv = new WriteClass(new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
            cv = new PrivilizingVisitor(this, cv);

            classReader.accept(cv, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(bytecode);
        }
    }

    void verify(final String className, final byte[] bytecode) {
        final ClassReader reader = new ClassReader(bytecode);

        env.debug("Verifying bytecode for class %s", className);
        final StringWriter w = new StringWriter();
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

        final ClassVisitor checkInnerClasses = new ClassVisitor(Opcodes.ASM4, null) {
            final Set<String> innerNames = new HashSet<String>();

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                super.visitInnerClass(name, outerName, innerName, access);
                Validate.validState(innerNames.add(innerName), "%s already defined", innerName);
            }
        };
        reader.accept(checkInnerClasses, ClassReader.SKIP_CODE);
    }
}
