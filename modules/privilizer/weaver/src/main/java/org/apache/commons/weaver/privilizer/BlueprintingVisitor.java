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

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * {@link ClassVisitor} to import so-called "blueprint methods".
 */
class BlueprintingVisitor extends Privilizer.PrivilizerClassVisitor {

    private final Set<Type> blueprintTypes = new HashSet<>();
    private final Map<Pair<Type, Method>, MethodNode> blueprintRegistry = new HashMap<>();

    private final Map<Pair<Type, Method>, String> importedMethods = new HashMap<>();

    private final Map<Type, Map<Method, MethodNode>> methodCache = new HashMap<>();
    private final Map<Pair<Type, String>, FieldAccess> fieldAccessMap = new HashMap<>();

    private final ClassVisitor nextVisitor;

    /**
     * Create a new {@link BlueprintingVisitor}.
     * @param privilizer owner
     * @param nextVisitor wrapped
     * @param config annotation
     */
    BlueprintingVisitor(@SuppressWarnings("PMD.UnusedFormalParameter") final Privilizer privilizer, //false positive
        final ClassVisitor nextVisitor,
        final Privilizing config) {
        privilizer.super(new ClassNode(Privilizer.ASM_VERSION));
        this.nextVisitor = nextVisitor;

        // load up blueprint methods:
        for (final Privilizing.CallTo callTo : config.value()) {
            final Type blueprintType = Type.getType(callTo.value());
            blueprintTypes.add(blueprintType);
            for (final Map.Entry<Method, MethodNode> entry : getMethods(blueprintType).entrySet()) {
                boolean found = false;
                if (callTo.methods().length == 0) {
                    found = true;
                } else {
                    for (final String name : callTo.methods()) {
                        if (entry.getKey().getName().equals(name)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    blueprintRegistry.put(Pair.of(blueprintType, entry.getKey()), entry.getValue());
                }
            }
        }
    }

    private Map<Method, MethodNode> getMethods(final Type type) {
        if (methodCache.containsKey(type)) {
            return methodCache.get(type);
        }
        final ClassNode classNode = read(type.getClassName());
        final Map<Method, MethodNode> result = new HashMap<>();

        final List<MethodNode> methods = classNode.methods;

        for (final MethodNode methodNode : methods) {
            if (Modifier.isStatic(methodNode.access) && !"<clinit>".equals(methodNode.name)) {
                result.put(new Method(methodNode.name, methodNode.desc), methodNode);
            }
        }
        methodCache.put(type, result);
        return result;
    }

    private ClassNode read(final String className) {
        final ClassNode result = new ClassNode(Privilizer.ASM_VERSION);
        try (InputStream bytecode = privilizer().env.getClassfile(className).getInputStream();) {
            new ClassReader(bytecode).accept(result, ClassReader.SKIP_DEBUG | ClassReader.EXPAND_FRAMES);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    @SuppressWarnings("PMD.UseVarargs") //overridden method
    public void visit(final int version, final int access, final String name, final String signature,
        final String superName, final String[] interfaces) {
        Validate.isTrue(!blueprintTypes.contains(Type.getObjectType(name)),
            "Class %s cannot declare itself as a blueprint!", name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    @SuppressWarnings("PMD.UseVarargs") //overridden method
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
        final String[] exceptions) {
        final MethodVisitor toWrap = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodInvocationHandler(toWrap) {
            @Override
            boolean shouldImport(final Pair<Type, Method> methodKey) {
                return blueprintRegistry.containsKey(methodKey);
            }
        };
    }

    private String importMethod(final Pair<Type, Method> key) {
        if (importedMethods.containsKey(key)) {
            return importedMethods.get(key);
        }
        final String result =
            new StringBuilder(key.getLeft().getInternalName().replace('/', '_')).append("$$")
                .append(key.getRight().getName()).toString();
        importedMethods.put(key, result);
        privilizer().env.debug("importing %s#%s as %s", key.getLeft().getClassName(), key.getRight(), result);
        final int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC;

        final MethodNode source = getMethods(key.getLeft()).get(key.getRight());

        final String[] exceptions = source.exceptions.toArray(ArrayUtils.EMPTY_STRING_ARRAY);

        // non-public fields accessed
        final Set<FieldAccess> fieldAccesses = new LinkedHashSet<>();

        source.accept(new MethodVisitor(Privilizer.ASM_VERSION) {
            @Override
            public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
                final FieldAccess fieldAccess = fieldAccess(Type.getObjectType(owner), name);

                super.visitFieldInsn(opcode, owner, name, desc);
                if (!Modifier.isPublic(fieldAccess.access)) {
                    fieldAccesses.add(fieldAccess);
                }
            }
        });

        final MethodNode withAccessibleAdvice =
            new MethodNode(access, result, source.desc, source.signature, exceptions);

        // spider own methods:
        MethodVisitor mv = new NestedMethodInvocationHandler(withAccessibleAdvice, key.getLeft()); //NOPMD

        if (!fieldAccesses.isEmpty()) {
            mv = new AccessibleAdvisor(mv, access, result, source.desc, new ArrayList<>(fieldAccesses));
        }
        source.accept(mv);

        // private can only be called by other privileged methods, so no need to mark as privileged
        if (!Modifier.isPrivate(source.access)) {
            withAccessibleAdvice.visitAnnotation(Type.getType(Privileged.class).getDescriptor(), false).visitEnd();
        }
        withAccessibleAdvice.accept(this.cv);

        return result;
    }

    private FieldAccess fieldAccess(final Type owner, final String name) {
        final Pair<Type, String> key = Pair.of(owner, name);
        if (!fieldAccessMap.containsKey(key)) {
            try {
                final MutableObject<Type> next = new MutableObject<>(owner);
                final Deque<Type> stk = new ArrayDeque<>();
                while (next.getValue() != null) {
                    stk.push(next.getValue());
                    try (InputStream bytecode =
                        privilizer().env.getClassfile(next.getValue().getInternalName()).getInputStream()) {
                        new ClassReader(bytecode).accept(privilizer().new PrivilizerClassVisitor() {
                            @Override
                            @SuppressWarnings("PMD.UseVarargs") // overridden method
                            public void visit(final int version, final int access, final String name,
                                final String signature, final String superName, final String[] interfaces) {
                                super.visit(version, access, name, signature, superName, interfaces);
                                next.setValue(Type.getObjectType(superName));
                            }

                            @Override
                            public FieldVisitor visitField(final int access, final String name, final String desc,
                                final String signature, final Object value) {
                                for (final Type type : stk) {
                                    final Pair<Type, String> key = Pair.of(type, name);
                                    // skip shadowed fields:
                                    if (!fieldAccessMap.containsKey(key)) {
                                        fieldAccessMap.put(key,
                                            new FieldAccess(access, target, name, Type.getType(desc)));
                                    }
                                }
                                return null;
                            }
                        }, ClassReader.SKIP_CODE);
                    }
                    if (fieldAccessMap.containsKey(key)) {
                        break;
                    }
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            Validate.isTrue(fieldAccessMap.containsKey(key), "Could not locate %s.%s", owner.getClassName(), name);
        }
        return fieldAccessMap.get(key);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        ((ClassNode) cv).accept(nextVisitor);
    }

    private abstract class MethodInvocationHandler extends MethodVisitor {
        MethodInvocationHandler(final MethodVisitor mvr) {
            super(Privilizer.ASM_VERSION, mvr);
        }

        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc,
            final boolean itf) {
            if (opcode == Opcodes.INVOKESTATIC) {
                final Method methd = new Method(name, desc);
                final Pair<Type, Method> methodKey = Pair.of(Type.getObjectType(owner), methd);
                if (shouldImport(methodKey)) {
                    final String importedName = importMethod(methodKey);
                    super.visitMethodInsn(opcode, className, importedName, desc, itf);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        abstract boolean shouldImport(Pair<Type, Method> methodKey);
    }

    class NestedMethodInvocationHandler extends MethodInvocationHandler {
        final Type owner;

        NestedMethodInvocationHandler(final MethodVisitor mvr, final Type owner) {
            super(mvr);
            this.owner = owner;
        }

        @Override
        boolean shouldImport(final Pair<Type, Method> methodKey) {
            // call anything called within a class hierarchy:
            final Type called = methodKey.getLeft();
            // "I prefer the short cut":
            if (called.equals(owner)) {
                return true;
            }
            try {
                final Class<?> inner = load(called);
                final Class<?> outer = load(owner);
                return inner.isAssignableFrom(outer);
            } catch (final ClassNotFoundException e) {
                return false;
            }
        }

        private Class<?> load(final Type type) throws ClassNotFoundException {
            return privilizer().env.classLoader.loadClass(type.getClassName());
        }
    }

    /**
     * For every non-public referenced field of an imported method, replaces with reflective calls. Additionally, for
     * every such field that is not accessible, sets the field's accessibility and clears it as the method exits.
     */
    private class AccessibleAdvisor extends AdviceAdapter {
        final Type bitSetType = Type.getType(BitSet.class);
        final Type classType = Type.getType(Class.class);
        final Type fieldType = Type.getType(java.lang.reflect.Field.class);
        final Type fieldArrayType = Type.getType(java.lang.reflect.Field[].class);
        final Type stringType = Type.getType(String.class);

        final List<FieldAccess> fieldAccesses;
        final Label begin = new Label();
        int localFieldArray;
        int bitSet;
        int fieldCounter;

        AccessibleAdvisor(final MethodVisitor mvr, final int access, final String name, final String desc,
            final List<FieldAccess> fieldAccesses) {
            super(Privilizer.ASM_VERSION, mvr, access, name, desc);
            this.fieldAccesses = fieldAccesses;
        }

        @Override
        protected void onMethodEnter() {
            localFieldArray = newLocal(fieldArrayType);
            bitSet = newLocal(bitSetType);
            fieldCounter = newLocal(Type.INT_TYPE);

            // create localFieldArray
            push(fieldAccesses.size());
            newArray(fieldArrayType.getElementType());
            storeLocal(localFieldArray);

            // create bitSet
            newInstance(bitSetType);
            dup();
            push(fieldAccesses.size());
            invokeConstructor(bitSetType, Method.getMethod("void <init>(int)"));
            storeLocal(bitSet);

            // populate localFieldArray
            push(0);
            storeLocal(fieldCounter);
            for (final FieldAccess access : fieldAccesses) {
                prehandle(access);
                iinc(fieldCounter, 1);
            }
            mark(begin);
        }

        private void prehandle(final FieldAccess access) {
            // push owner.class literal
            visitLdcInsn(access.owner);
            push(access.name);
            final Label next = new Label();
            invokeVirtual(classType, new Method("getDeclaredField", fieldType, new Type[] { stringType }));

            dup();
            // store the field at localFieldArray[fieldCounter]:
            loadLocal(localFieldArray);
            swap();
            loadLocal(fieldCounter);
            swap();
            arrayStore(fieldArrayType.getElementType());

            dup();
            invokeVirtual(fieldArrayType.getElementType(), Method.getMethod("boolean isAccessible()"));

            final Label setAccessible = new Label();
            // if false, setAccessible:
            ifZCmp(EQ, setAccessible);

            // else pop field instance
            pop();
            // and record that he was already accessible:
            loadLocal(bitSet);
            loadLocal(fieldCounter);
            invokeVirtual(bitSetType, Method.getMethod("void set(int)"));
            goTo(next);

            mark(setAccessible);
            push(true);
            invokeVirtual(fieldArrayType.getElementType(), Method.getMethod("void setAccessible(boolean)"));

            mark(next);
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
            final Pair<Type, String> key = Pair.of(Type.getObjectType(owner), name);
            final FieldAccess fieldAccess = fieldAccessMap.get(key);
            Validate.isTrue(fieldAccesses.contains(fieldAccess), "Cannot find field %s", key);
            final int fieldIndex = fieldAccesses.indexOf(fieldAccess);
            visitInsn(NOP);
            loadLocal(localFieldArray);
            push(fieldIndex);
            arrayLoad(fieldArrayType.getElementType());
            checkCast(fieldType);

            final Method access;
            if (opcode == PUTSTATIC) {
                // value should have been at top of stack on entry; position the field under the value:
                swap();
                // add null object for static field deref and swap under value:
                push((String) null);
                swap();
                if (fieldAccess.type.getSort() < Type.ARRAY) {
                    // box value:
                    valueOf(fieldAccess.type);
                }
                access = Method.getMethod("void set(Object, Object)");
            } else {
                access = Method.getMethod("Object get(Object)");
                // add null object for static field deref:
                push((String) null);
            }

            invokeVirtual(fieldType, access);

            if (opcode == GETSTATIC) {
                checkCast(privilizer().wrap(fieldAccess.type));
                if (fieldAccess.type.getSort() < Type.ARRAY) {
                    unbox(fieldAccess.type);
                }
            }
        }

        @Override
        public void visitMaxs(final int maxStack, final int maxLocals) {
            // put try-finally around the whole method
            final Label fny = mark();
            // null exception type signifies finally block:
            final Type exceptionType = null;
            catchException(begin, fny, exceptionType);
            onFinally();
            throwException();
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        protected void onMethodExit(final int opcode) {
            if (opcode != ATHROW) {
                onFinally();
            }
        }

        private void onFinally() {
            // loop over fields and return any non-null element to being inaccessible:
            push(0);
            storeLocal(fieldCounter);

            final Label test = mark();
            final Label increment = new Label();
            final Label endFinally = new Label();

            loadLocal(fieldCounter);
            push(fieldAccesses.size());
            ifCmp(Type.INT_TYPE, GeneratorAdapter.GE, endFinally);

            loadLocal(bitSet);
            loadLocal(fieldCounter);
            invokeVirtual(bitSetType, Method.getMethod("boolean get(int)"));

            // if true, increment:
            ifZCmp(NE, increment);

            loadLocal(localFieldArray);
            loadLocal(fieldCounter);
            arrayLoad(fieldArrayType.getElementType());
            push(false);
            invokeVirtual(fieldArrayType.getElementType(), Method.getMethod("void setAccessible(boolean)"));

            mark(increment);
            iinc(fieldCounter, 1);
            goTo(test);
            mark(endFinally);
        }
    }
}
