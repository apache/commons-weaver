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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.StaticInitMerger;

/**
 * ASM {@link ClassVisitor} to privilize {@link Privileged} methods.
 */
class PrivilizingVisitor extends Privilizer.PrivilizerClassVisitor {
    final Map<Method, String> privilegedMethods = new LinkedHashMap<>();
    boolean annotated;
    final Policy policy;
    final AccessLevel accessLevel;

    /**
     * Create a new {@link PrivilizingVisitor}.
     * @param privilizer owner
     * @param cv next
     */
    PrivilizingVisitor(final Privilizer privilizer, final ClassVisitor cv) { //NOPMD
        privilizer.super();
        this.policy = privilizer.policy;
        this.accessLevel = privilizer.accessLevel;
        this.cv =
            new InlineNestedPrivilegedCalls(privilizer, privilegedMethods, new StaticInitMerger(
                privilizer.generateName("clinit"), cv));
    }

    private void annotate() {
        if (!annotated) {
            annotated = true;
            final AnnotationVisitor privilizedVisitor =
                super.visitAnnotation(Type.getType(Privilized.class).getDescriptor(), false);
            privilizedVisitor.visit("value", policy.name());
            privilizedVisitor.visitEnd();
        }
    }

    @Override
    public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
        annotate();
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
        final Object value) {
        annotate();
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    @SuppressWarnings("PMD.UseVarargs") //overridden method
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
        final String[] exceptions) {
        annotate();
        final MethodVisitor originalMethod = super.visitMethod(access, name, desc, signature, exceptions);
        final Method methd = new Method(name, desc);

        return new GeneratorAdapter(Privilizer.ASM_VERSION, originalMethod, access, name, desc) {

            @Override
            public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                if (Type.getType(Privileged.class).getDescriptor().equals(desc)) {
                    final AccessLevel localAccessLevel = AccessLevel.of(access);
                    if (accessLevel.compareTo(localAccessLevel) > 0) {
                        throw new IllegalStateException(new IllegalAccessException("Method " + className + "#" + methd
                            + " must have maximum access level '" + accessLevel + "' but is defined wider ('"
                            + localAccessLevel + "')"));
                    }
                    if (AccessLevel.PACKAGE.compareTo(accessLevel) > 0) {
                        privilizer().env.warn("Possible security leak: granting privileges to %s method %s.%s",
                            localAccessLevel, className, methd);
                    }
                    privilegedMethods.put(methd, privilizer().generateName(name));
                }
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visitCode() {
                super.visitCode();
                if (!privilegedMethods.containsKey(methd)) {
                    return;
                }
                final String impl = privilegedMethods.get(methd);
                final boolean instanceMethod = !Modifier.isStatic(access);

                if (policy.isConditional()) {
                    privilizer().env.debug("setting up conditional execution due to policy %s", policy);
                    // test, loading boolean
                    if (policy == Policy.ON_INIT) {
                        getStatic(target, privilizer().generateName("hasSecurityManager"), Type.BOOLEAN_TYPE);
                    } else if (policy == Policy.DYNAMIC) {
                        checkSecurityManager(this);
                    }
                    final Label doPrivileged = new Label();

                    // if true, goto doPrivileged:
                    ifZCmp(NE, doPrivileged);

                    final Method implMethod = new Method(impl, desc);
                    if (instanceMethod) {
                        loadThis();
                        loadArgs();
                        invokeVirtual(target, implMethod);
                    } else {
                        loadArgs();
                        invokeStatic(target, implMethod);
                    }
                    returnValue();
                    mark(doPrivileged);
                } else {
                    privilizer().env.debug("setting up unconditional privileged execution due to policy %s", policy);
                }
                // generate action:
                final Type[] ctorArgs =
                    instanceMethod ? ArrayUtils.insert(0, methd.getArgumentTypes(), target) : methd.getArgumentTypes();
                final Type actionType = new ActionGenerator(access, methd, exceptions, PrivilizingVisitor.this).build();
                newInstance(actionType);
                dup();
                if (instanceMethod) {
                    loadThis();
                }
                loadArgs();
                invokeConstructor(actionType, new Method("<init>", Type.VOID_TYPE, ctorArgs));

                final boolean exc = ArrayUtils.isNotEmpty(exceptions);
                // mark try if needed
                final Label privTry = exc ? mark() : null;

                // execute action
                final Type arg =
                    exc ? Type.getType(PrivilegedExceptionAction.class) : Type.getType(PrivilegedAction.class);
                final Method doPrivileged = new Method("doPrivileged", Type.getType(Object.class), new Type[] { arg });
                invokeStatic(Type.getType(AccessController.class), doPrivileged);

                unbox(methd.getReturnType());
                returnValue();

                if (exc) {
                    final Type caught = Type.getType(PrivilegedActionException.class);
                    // end try
                    final Label privCatch = mark();
                    // catch
                    catchException(privTry, privCatch, caught);
                    // unwrap
                    invokeVirtual(caught, new Method("getException", Type.getType(Exception.class),
                        Privilizer.EMPTY_TYPE_ARRAY));
                    // throw
                    throwException();
                }
                // end original method
                endMethod();

                // substitute an impl visitor and continue
                mv = cv.visitMethod(AccessLevel.PRIVATE.merge(access), impl, desc, signature, exceptions);
                mv.visitCode();
            }
        };
    }

    @Override
    public void visitEnd() {
        annotate();
        if (privilizer().policy == Policy.ON_INIT) {
            final String fieldName = privilizer().generateName("hasSecurityManager");

            visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, fieldName,
                Type.BOOLEAN_TYPE.getDescriptor(), null, null).visitEnd();

            final GeneratorAdapter mgen =
                new GeneratorAdapter(Opcodes.ACC_STATIC, new Method("<clinit>", "()V"), null,
                    Privilizer.EMPTY_TYPE_ARRAY, this);
            checkSecurityManager(mgen);
            mgen.putStatic(target, fieldName, Type.BOOLEAN_TYPE);
            mgen.returnValue();
            mgen.endMethod();
        }
        super.visitEnd();
    }

    /**
     * Generates the instructions to push onto the stack whether there is a
     * security manager available.
     * @param mgen to control
     */
    static void checkSecurityManager(final GeneratorAdapter mgen) {
        final Label setFalse = new Label();
        final Label done = new Label();
        mgen.invokeStatic(Type.getType(System.class),
            new Method("getSecurityManager", Type.getType(SecurityManager.class), Privilizer.EMPTY_TYPE_ARRAY));
        mgen.ifNull(setFalse);
        mgen.push(true);
        mgen.goTo(done);
        mgen.mark(setFalse);
        mgen.push(false);
        mgen.mark(done);
    }
}
