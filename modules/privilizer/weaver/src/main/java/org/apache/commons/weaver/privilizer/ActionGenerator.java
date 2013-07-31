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

import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import javassist.Modifier;

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
    final Method m;
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

    ActionGenerator(final int access, final Method m, final String[] exceptions, PrivilizingVisitor owner) {
        owner.privilizer().super(new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES));
        this.m = m;
        this.exc = ArrayUtils.isNotEmpty(exceptions);
        this.exceptions = exc ? new Type[] { Type.getType(Exception.class) } : null;
        this.owner = owner;
        this.simpleName = generateName(m);
        this.action = Type.getObjectType(owner.className + '$' + simpleName);

        int privilegedAccessIndex = -1;
        String implName = null;
        for (Map.Entry<Method, String> e : owner.privilegedMethods.entrySet()) {
            privilegedAccessIndex++;
            if (e.getKey().equals(m)) {
                implName = e.getValue();
                break;
            }
        }
        Validate.validState(implName != null);

        this.index = privilegedAccessIndex;

        this.impl = new Method(implName, m.getDescriptor());
        this.implIsStatic = Modifier.isStatic(access);
        final Type[] args = implIsStatic ? m.getArgumentTypes() : ArrayUtils.add(m.getArgumentTypes(), 0, owner.target);
        this.helper = new Method(privilizer().generateName("access$" + index), m.getReturnType(), args);
        this.result = privilizer().wrap(m.getReturnType());
        this.fields = fields(args);
        this.actionInterface = Type.getType(exc ? PrivilegedExceptionAction.class : PrivilegedAction.class);
    }

    private static String generateName(Method m) {
        final StringBuilder b = new StringBuilder(m.getName());
        if (m.getArgumentTypes().length > 0) {
            b.append("$$");
            for (Type arg : m.getArgumentTypes()) {
                b.append(arg.getDescriptor().replace("[", "arrayOf").replace('/', '_').replace(';', '$'));
            }
        }
        return b.append("_ACTION").toString();
    }

    private static Field[] fields(Type[] args) {
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
        final GeneratorAdapter mg =
            new GeneratorAdapter(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, helper, null, exceptions, owner);

        mg.visitCode();
        mg.loadArgs();
        if (implIsStatic) {
            mg.invokeStatic(owner.target, impl);
        } else {
            mg.invokeVirtual(owner.target, impl);
        }
        mg.returnValue();
        mg.endMethod();
    }

    private void begin() {
        owner.visitInnerClass(action.getInternalName(), owner.className, simpleName, Opcodes.ACC_PRIVATE
            | Opcodes.ACC_STATIC);

        final SignatureWriter type = new SignatureWriter();
        final SignatureVisitor actionImplemented = type.visitInterface();
        actionImplemented.visitClassType(actionInterface.getInternalName());
        final SignatureVisitor visitTypeArgument = actionImplemented.visitTypeArgument('=');
        final SignatureReader result = new SignatureReader(privilizer().wrap(m.getReturnType()).getDescriptor());
        result.accept(visitTypeArgument);
        actionImplemented.visitEnd();

        final String signature = type.toString();

        visit(Opcodes.V1_5, Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL, action.getInternalName(),
            signature, Type.getType(Object.class).getInternalName(), new String[] { actionInterface.getInternalName() });
    }

    /**
     * Add fields and generate constructor.
     * 
     * @param cv
     */
    private void init() {
        for (Field field : fields) {
            visitField(field.access, field.name, field.type.getDescriptor(), null, null).visitEnd();
        }
        final Method init = new Method("<init>", Type.VOID_TYPE, helper.getArgumentTypes());

        final GeneratorAdapter mg =
            new GeneratorAdapter(0, init, null, Privilizer.EMPTY_TYPE_ARRAY, this);

        mg.visitCode();
        final Label begin = mg.mark();

        // invoke super constructor
        mg.loadThis();
        mg.invokeConstructor(Type.getType(Object.class), Method.getMethod("void <init> ()"));
        // assign remaining fields

        int arg = 0;
        for (Field field : fields) {
            mg.loadThis();
            mg.loadArg(arg++);
            mg.putField(action, field.name, field.type);
        }

        mg.returnValue();
        final Label end = mg.mark();

        // declare local vars
        mg.visitLocalVariable("this", action.getDescriptor(), null, begin, end, 0);
        arg = 1;
        for (Field field : fields) {
            mg.visitLocalVariable("arg" + arg, field.type.getDescriptor(), null, begin, end, arg++);
        }
        mg.endMethod();
    }

    /**
     * Generate impl method.
     * 
     * @param cv
     */
    private void impl() {
        final Method run = Method.getMethod("Object run()");

        final GeneratorAdapter mg = new GeneratorAdapter(Opcodes.ACC_PUBLIC, run, null, exceptions, this);

        for (Field field : fields) {
            mg.loadThis();
            mg.getField(action, field.name, field.type);
        }

        mg.invokeStatic(owner.target, helper);

        if (m.getReturnType().getSort() < Type.ARRAY) {
            mg.valueOf(m.getReturnType());
        }

        mg.returnValue();

        mg.endMethod();
    }

}
