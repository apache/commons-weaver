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
import java.lang.invoke.LambdaMetafactory;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * {@link ClassVisitor} to import so-called "blueprint methods".
 */
class BlueprintingVisitor extends Privilizer.PrivilizerClassVisitor {
    static class TypeInfo {
        final int access;
        final String superName;
        final Map<String, FieldNode> fields;
        final Map<Method, MethodNode> methods;

        TypeInfo(final int access, final String superName, final Map<String, FieldNode> fields,
            final Map<Method, MethodNode> methods) {
            super();
            this.access = access;
            this.superName = superName;
            this.fields = fields;
            this.methods = methods;
        }
    }

    /**
     * ASM {@link Type} for {@link LambdaMetafactory}.
     */
    static final Type LAMBDA_METAFACTORY = Type.getType(LambdaMetafactory.class);

    /**
     * Compute a method key from the specified parameters.
     * @param owner
     * @param name
     * @param desc
     * @return {@link Pair} of {@link Type} and {@link Method}
     */
    static Pair<Type, Method> methodKey(final String owner, final String name, final String desc) {
        return Pair.of(Type.getObjectType(owner), new Method(name, desc));
    }

    /**
     * Blueprint registry {@link Map}.
     */
    final Map<Pair<Type, Method>, MethodNode> blueprintRegistry = new HashMap<>();

    /**
     * Field access map.
     */
    final Map<Pair<Type, String>, FieldAccess> fieldAccessMap = new HashMap<>();

    private final Set<Type> blueprintTypes = new HashSet<>();

    private final Map<Pair<Type, Method>, String> importedMethods = new HashMap<>();

    private final Map<Type, TypeInfo> typeInfoCache = new HashMap<>();

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

            final Set<String> methodNames = new HashSet<>(Arrays.asList(callTo.methods()));

            typeInfo(blueprintType).methods.entrySet().stream()
                .filter(e -> methodNames.isEmpty() || methodNames.contains(e.getKey().getName()))
                .forEach(e -> blueprintRegistry.put(Pair.of(blueprintType, e.getKey()), e.getValue()));
        }
    }

    /**
     * Compute {@link TypeInfo} for the specified {@link Type}.
     * @param type
     * @return {@link TypeInfo}
     */
    TypeInfo typeInfo(final Type type) {
        return typeInfoCache.computeIfAbsent(type, k -> {
            final ClassNode classNode = read(k.getClassName());

            return new TypeInfo(classNode.access, classNode.superName,
                classNode.fields.stream().collect(Collectors.toMap(f -> f.name, Function.identity())), classNode.methods
                    .stream().collect(Collectors.toMap(m -> new Method(m.name, m.desc), Function.identity())));
        });
    }

    private ClassNode read(final String className) {
        final ClassNode result = new ClassNode(Privilizer.ASM_VERSION);
        try (InputStream bytecode = privilizer().env.getClassfile(className).getInputStream()) {
            new ClassReader(bytecode).accept(result, ClassReader.SKIP_DEBUG | ClassReader.EXPAND_FRAMES);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
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

    /**
     * Import the method specified by {@code key}.
     * @param key
     * @return {@link String} method name
     */
    String importMethod(final Pair<Type, Method> key) {
        if (importedMethods.containsKey(key)) {
            return importedMethods.get(key);
        }
        final String result =
            new StringBuilder(key.getLeft().getInternalName().replace('/', '_')).append("$$")
                .append(key.getRight().getName()).toString();
        importedMethods.put(key, result);
        privilizer().env.debug("importing %s#%s as %s", key.getLeft().getClassName(), key.getRight(), result);
        final int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC;

        final MethodNode source = typeInfo(key.getLeft()).methods.get(key.getRight());

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
        MethodVisitor mv = new NestedMethodInvocationHandler(withAccessibleAdvice, key); //NOPMD

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

    /**
     * Compute a {@link FieldAccess} object for the specified parameters.
     * @param owner
     * @param name
     * @return {@link FieldAccess}
     */
    FieldAccess fieldAccess(final Type owner, final String name) {
        return fieldAccessMap.computeIfAbsent(Pair.of(owner, name), k -> {
            final FieldNode fieldNode = typeInfo(k.getLeft()).fields.get(k.getRight());
            Validate.validState(fieldNode != null, "Could not locate %s.%s", k.getLeft().getClassName(), k.getRight());
            return new FieldAccess(fieldNode.access, k.getLeft(), fieldNode.name, Type.getType(fieldNode.desc));
        });
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
                final Pair<Type, Method> methodKey = methodKey(owner, name, desc);
                if (shouldImport(methodKey)) {
                    final String importedName = importMethod(methodKey);
                    super.visitMethodInsn(opcode, className, importedName, desc, itf);
                    return;
                }
            }
            visitNonImportedMethodInsn(opcode, owner, name, desc, itf);
        }

        protected void visitNonImportedMethodInsn(final int opcode, final String owner, final String name,
            final String desc, final boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitInvokeDynamicInsn(final String name, final String descriptor,
            final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments) {

            if (!(isLambda(bootstrapMethodHandle)
                && invokeDynamicImportedMethod(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments))) {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }

        /**
         * May be overridden by subclasses.
         * @param handle
         */
        protected void validateLambda(final Handle handle) {
            // base implementation does nothing
        }

        /**
         * Learn whether the method specified by {@code methodKey} should be imported.
         * @param methodKey
         * @return {@code boolean}
         */
        abstract boolean shouldImport(Pair<Type, Method> methodKey);

        private boolean isLambda(final Handle handle) {
            return handle.getTag() == Opcodes.H_INVOKESTATIC
                && LAMBDA_METAFACTORY.getInternalName().equals(handle.getOwner())
                && "metafactory".equals(handle.getName());
        }

        private boolean invokeDynamicImportedMethod(final String name, final String descriptor,
            final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments) {

            OptionalInt handleIndex = OptionalInt.empty();

            for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                if (bootstrapMethodArguments[i] instanceof Handle) {
                    if (handleIndex.isPresent()) {
                        return false;
                    }
                    handleIndex = OptionalInt.of(i);
                }
            }
            if (handleIndex.isPresent()) {
                final Handle handle = (Handle) bootstrapMethodArguments[handleIndex.getAsInt()];
                if (handle.getTag() == Opcodes.H_INVOKESTATIC) {
                    final Pair<Type, Method> methodKey =
                        methodKey(handle.getOwner(), handle.getName(), handle.getDesc());

                    if (shouldImport(methodKey)) {
                        final String importedName = importMethod(methodKey);
                        final Object[] args = bootstrapMethodArguments.clone();

                        args[handleIndex.getAsInt()] =
                            new Handle(handle.getTag(), className, importedName, handle.getDesc(), false);

                        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, args);
                        return true;
                    }
                    validateLambda(handle);
                }
            }
            return false;
        }
    }

    class NestedMethodInvocationHandler extends MethodInvocationHandler {
        final Pair<Type, Method> methodKey;
        final Type owner;

        NestedMethodInvocationHandler(final MethodVisitor mvr, final Pair<Type, Method> methodKey) {
            super(mvr);
            this.methodKey = methodKey;
            this.owner = methodKey.getLeft();
        }

        @Override
        protected void visitNonImportedMethodInsn(final int opcode, final String owner, final String name,
            final String desc, final boolean itf) {
            final Type ownerType = Type.getObjectType(owner);
            final Method method = new Method(name, desc);

            if (isAccessible(ownerType) && isAccessible(ownerType, method)) {
                super.visitNonImportedMethodInsn(opcode, owner, name, desc, itf);
            } else {
                throw new IllegalStateException(String.format("Blueprint method %s.%s calls inaccessible method %s.%s",
                    this.owner, methodKey.getRight(), owner, method));
            }
        }

        @Override
        protected void validateLambda(final Handle handle) {
            super.validateLambda(handle);
            final Type ownerType = Type.getObjectType(handle.getOwner());
            final Method method = new Method(handle.getName(), handle.getDesc());

            if (!(isAccessible(ownerType) && isAccessible(ownerType, method))) {
                throw new IllegalStateException(
                    String.format("Blueprint method %s.%s utilizes inaccessible method reference %s::%s", owner,
                        methodKey.getRight(), handle.getOwner(), method));
            }
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

        private boolean isAccessible(final Type type) {
            final TypeInfo typeInfo = typeInfo(type);
            return isAccessible(type, typeInfo.access);
        }

        private boolean isAccessible(final Type type, final Method method) {
            Type currentType = type;
            while (currentType != null) {
                final TypeInfo typeInfo = typeInfo(currentType);
                final MethodNode methodNode = typeInfo.methods.get(method);
                if (methodNode == null) {
                    currentType = Optional.ofNullable(typeInfo.superName).map(Type::getObjectType).orElse(null);
                    continue;
                }
                return isAccessible(type, methodNode.access);
            }
            throw new IllegalStateException(String.format("Cannot find method %s.%s", type, method));
        }

        private boolean isAccessible(final Type type, final int access) {
            if (Modifier.isPublic(access)) {
                return true;
            }
            if (Modifier.isProtected(access) || Modifier.isPrivate(access)) {
                return false;
            }
            return Stream.of(target, type).map(Type::getInternalName).map(n -> StringUtils.substringBeforeLast(n, "/"))
                    .distinct().count() == 1;
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
                checkCast(Privilizer.wrap(fieldAccess.type));
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
