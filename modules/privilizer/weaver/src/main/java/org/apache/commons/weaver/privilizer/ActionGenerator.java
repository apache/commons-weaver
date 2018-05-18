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

import java.lang.reflect.Modifier;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.Builder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

/**
 * Generates the Privileged[Exception?]Action class to privilize a given Method.
 */
class ActionGenerator extends Privilizer.WriteClass implements Builder<Type> {
    final PrivilizingVisitor owner;
    final Method methd;
    final boolean exc;
    final Type[] exceptions;
    final String simpleName;
    final Type action;
    final Method impl;
    final int index;
    final boolean implIsStatic;
    final Method helper;
    final Type result;
    final Field[] fields;
    private final Type actionInterface;

    /**
     * Create a new {@link ActionGenerator}.
     * @param access modifier
     * @param methd {@link Method} to implement
     * @param exceptions thrown
     * @param owner of the action class
     */
    ActionGenerator(final int access, final Method methd, final String[] exceptions, final PrivilizingVisitor owner) {
        owner.privilizer().super(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.methd = methd;
        this.exc = ArrayUtils.isNotEmpty(exceptions);
        this.exceptions = exc ? new Type[] { Type.getType(Exception.class) } : null;
        this.owner = owner;
        this.simpleName = generateName(methd);
        this.action = Type.getObjectType(owner.className + '$' + simpleName);

        int privilegedAccessIndex = -1;
        String implName = null;
        for (final Map.Entry<Method, String> entry : owner.privilegedMethods.entrySet()) {
            privilegedAccessIndex++;
            if (entry.getKey().equals(methd)) {
                implName = entry.getValue();
                break;
            }
        }
        Validate.validState(implName != null);

        this.index = privilegedAccessIndex;

        this.impl = new Method(implName, methd.getDescriptor());
        this.implIsStatic = Modifier.isStatic(access);
        final Type[] args =
            implIsStatic ? methd.getArgumentTypes() : ArrayUtils.insert(0, methd.getArgumentTypes(), owner.target);
        this.helper = new Method(privilizer().generateName("access$" + index), methd.getReturnType(), args);
        this.result = Privilizer.wrap(methd.getReturnType());
        this.fields = fields(args);
        this.actionInterface = Type.getType(exc ? PrivilegedExceptionAction.class : PrivilegedAction.class);
    }

    private static String generateName(final Method methd) {
        final StringBuilder buf = new StringBuilder(methd.getName());
        if (methd.getArgumentTypes().length > 0) {
            buf.append("$$");
            for (final Type arg : methd.getArgumentTypes()) {
                buf.append(arg.getDescriptor().replace("[", "arrayOf").replace('/', '_').replace(';', '$'));
            }
        }
        return buf.append("_ACTION").toString();
    }

    @SuppressWarnings("PMD.UseVarargs") //not needed
    private static Field[] fields(final Type[] args) {
        final Field[] result = new Field[args.length];

        for (int i = 0; i < args.length; i++) {
            final String name = new StringBuilder("f").append(i + 1).toString();
            result[i] = new Field(Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL, name, args[i]);
        }
        return result;
    }

    @Override
    public Type build() {
        generateHelper();
        begin();
        init();
        impl();
        visitEnd();
        owner.privilizer().env.debug("Generated %s implementation %s to call %s#%s", actionInterface.getClassName(),
            action.getClassName(), owner.target.getClassName(), helper);
        return action;
    }

    /**
     * We must add special methods for inner classes to invoke their owners' methods, according to the scheme "access$n"
     * where n is the index into this (ordered) map. Additionally we will prefix the whole thing like we usually do
     * (__privileged_):
     */
    private void generateHelper() {
        owner.privilizer().env.debug("Generating static helper method %s.%s to call %s", owner.target.getClassName(),
            helper, impl);
        final GeneratorAdapter mgen =
            new GeneratorAdapter(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, helper, null, exceptions, owner);

        mgen.visitCode();
        mgen.loadArgs();
        if (implIsStatic) {
            mgen.invokeStatic(owner.target, impl);
        } else {
            mgen.invokeVirtual(owner.target, impl);
        }
        mgen.returnValue();
        mgen.endMethod();
    }

    private void begin() {
        owner.visitInnerClass(action.getInternalName(), owner.className, simpleName, Opcodes.ACC_PRIVATE
            | Opcodes.ACC_STATIC);

        final SignatureWriter type = new SignatureWriter();
        final SignatureVisitor actionImplemented = type.visitInterface();
        actionImplemented.visitClassType(actionInterface.getInternalName());
        final SignatureVisitor visitTypeArgument = actionImplemented.visitTypeArgument('=');
        new SignatureReader(Privilizer.wrap(methd.getReturnType()).getDescriptor()).accept(visitTypeArgument);
        actionImplemented.visitEnd();

        final String signature = type.toString();

        visit(Opcodes.V1_5, Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL, action.getInternalName(),
            signature, Type.getType(Object.class).getInternalName(),
            new String[] { actionInterface.getInternalName() });
    }

    /**
     * Add fields and generate constructor.
     */
    private void init() {
        for (final Field field : fields) {
            visitField(field.access, field.name, field.type.getDescriptor(), null, null).visitEnd();
        }
        final Method init = new Method("<init>", Type.VOID_TYPE, helper.getArgumentTypes());

        final GeneratorAdapter mgen =
            new GeneratorAdapter(0, init, null, Privilizer.EMPTY_TYPE_ARRAY, this);

        mgen.visitCode();
        final Label begin = mgen.mark();

        // invoke super constructor
        mgen.loadThis();
        mgen.invokeConstructor(Type.getType(Object.class), Method.getMethod("void <init> ()"));
        // assign remaining fields

        int arg = 0;
        for (final Field field : fields) {
            mgen.loadThis();
            mgen.loadArg(arg++);
            mgen.putField(action, field.name, field.type);
        }
        mgen.returnValue();
        final Label end = mgen.mark();

        // declare local vars
        mgen.visitLocalVariable("this", action.getDescriptor(), null, begin, end, 0);
        arg = 1;
        for (final Field field : fields) {
            mgen.visitLocalVariable("arg" + arg, field.type.getDescriptor(), null, begin, end, arg++);
        }
        mgen.endMethod();
    }

    /**
     * Generate impl method.
     */
    private void impl() {
        final Method run = Method.getMethod("Object run()");

        final GeneratorAdapter mgen = new GeneratorAdapter(Opcodes.ACC_PUBLIC, run, null, exceptions, this);

        for (final Field field : fields) {
            mgen.loadThis();
            mgen.getField(action, field.name, field.type);
        }
        mgen.invokeStatic(owner.target, helper);

        if (methd.getReturnType().getSort() < Type.ARRAY) {
            mgen.valueOf(methd.getReturnType());
        }
        mgen.returnValue();
        mgen.endMethod();
    }
}
