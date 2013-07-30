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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedHashMap;
import java.util.Map;

import javassist.Modifier;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.weaver.privilizer.AccessLevel;
import org.apache.commons.weaver.privilizer.Policy;
import org.apache.commons.weaver.privilizer.Privileged;
import org.apache.commons.weaver.privilizer.Privilized;
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

class PrivilizingVisitor extends Privilizer.PrivilizerClassVisitor {
    final Map<Method, String> privilegedMethods = new LinkedHashMap<Method, String>();
    boolean annotated;
    final Policy policy;
    final AccessLevel accessLevel;

    PrivilizingVisitor(Privilizer privilizer, ClassVisitor cv) {
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
            privilizedVisitor.visitEnum("value", Type.getType(Policy.class).getDescriptor(), policy.name());
            privilizedVisitor.visitEnd();
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        annotate();
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        annotate();
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
        final String[] exceptions) {
        annotate();
        final MethodVisitor originalMethod = super.visitMethod(access, name, desc, signature, exceptions);
        final Method m = new Method(name, desc);

        return new GeneratorAdapter(Opcodes.ASM4, originalMethod, access, name, desc) {

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (Type.getType(Privileged.class).getDescriptor().equals(desc)) {
                    final AccessLevel localAccessLevel = AccessLevel.of(access);
                    if (accessLevel.compareTo(localAccessLevel) > 0) {
                        throw new RuntimeException(new IllegalAccessException("Method " + className + "#" + m
                            + " must have maximum access level '" + accessLevel + "' but is defined wider ('"
                            + localAccessLevel + "')"));
                    }
                    if (AccessLevel.PACKAGE.compareTo(accessLevel) > 0) {
                        privilizer().env.warn("Possible security leak: granting privileges to %s method %s.%s",
                            localAccessLevel, className, m);
                    }
                    privilegedMethods.put(m, privilizer().generateName(name));
                }
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visitCode() {
                super.visitCode();
                if (!privilegedMethods.containsKey(m)) {
                    return;
                }
                final String impl = privilegedMethods.get(m);
                final boolean instanceMethod = !Modifier.isStatic(access);

                if (policy.isConditional()) {
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
                }

                // generate action:
                final Type[] ctorArgs;
                if (instanceMethod) {
                    ctorArgs = ArrayUtils.add(m.getArgumentTypes(), 0, target);
                } else {
                    ctorArgs = m.getArgumentTypes();
                }
                final Type actionType = new ActionGenerator(access, m, exceptions, PrivilizingVisitor.this).build();
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

                unbox(m.getReturnType());
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

    public void visitEnd() {
        annotate();
        if (privilizer().policy == Policy.ON_INIT) {
            final String fieldName = privilizer().generateName("hasSecurityManager");

            visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, fieldName,
                Type.BOOLEAN_TYPE.getDescriptor(), null, null).visitEnd();

            final GeneratorAdapter mg =
                new GeneratorAdapter(Opcodes.ACC_STATIC, new Method("<clinit>", "()V"), null,
                    Privilizer.EMPTY_TYPE_ARRAY, this);
            checkSecurityManager(mg);
            mg.putStatic(target, fieldName, Type.BOOLEAN_TYPE);
            mg.returnValue();
            mg.endMethod();

            super.visitEnd();
        }
    }

    /**
     * Generates the instructions to push onto the stack whether there is a security manager available
     * 
     * @param mg
     */
    private static void checkSecurityManager(GeneratorAdapter mg) {
        final Label setFalse = new Label();
        final Label done = new Label();
        mg.invokeStatic(Type.getType(System.class),
            new Method("getSecurityManager", Type.getType(SecurityManager.class), Privilizer.EMPTY_TYPE_ARRAY));
        mg.ifNull(setFalse);
        mg.push(true);
        mg.goTo(done);
        mg.mark(setFalse);
        mg.push(false);
        mg.mark(done);
    }
}
