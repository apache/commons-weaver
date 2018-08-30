/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.weaver.normalizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Conversion;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.spi.Weaver;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

/**
 * Handles the work of "normalizing" anonymous class definitions.
 */
public class Normalizer {

    private static final class Inspector extends ClassVisitor {
        private final class InspectConstructor extends MethodVisitor {
            private InspectConstructor() {
                super(ASM_VERSION);
            }

            @Override
            public void visitMethodInsn(final int opcode, final String owner, final String name,
                final String desc, final boolean itf) {
                if (INIT.equals(name) && owner.equals(superName)) {
                    key.setRight(desc);
                } else {
                    valid.setValue(false);
                }
            }

            @Override
            public void visitFieldInsn(final int opcode, final String owner, final String name,
                final String desc) {
                if ("this$0".equals(name) && opcode == Opcodes.PUTFIELD) {
                    mustRewriteConstructor.setValue(true);
                    return;
                }
                valid.setValue(false);
            }
        }

        /**
         * Supername of visited class.
         */
        String superName;

        /**
         * Key to identify "like" subclasses: abstract class or implemented interface + super ctor signature.
         */
        final MutablePair<String, String> key = new MutablePair<>();

        /**
         * Whether the constructor must be rewritten (true for non-{@code static} classes).
         */
        final MutableBoolean mustRewriteConstructor = new MutableBoolean(false);

        /**
         * Whether the inspected class is eligible to be normalized.
         */
        final MutableBoolean valid = new MutableBoolean(true);

        private final MutableBoolean ignore = new MutableBoolean(false);

        private Inspector() {
            super(ASM_VERSION);
        }

        @Override
        @SuppressWarnings("PMD.UseVarargs") //overridden method
        public void visit(final int version, final int access, final String name, final String signature,
            final String superName, final String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.superName = superName;
            final String left;
            if (signature != null) {
                left = signature;
            } else if (ArrayUtils.getLength(interfaces) == 1) {
                left = interfaces[0];
            } else {
                left = superName;
            }
            key.setLeft(left);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            if (Type.getType(Marker.class).getDescriptor().equals(desc)) {
                ignore.setValue(true);
            }
            return null;
        }

        @Override
        @SuppressWarnings("PMD.UseVarargs") //overridden method
        public MethodVisitor visitMethod(final int access, final String name, final String desc,
            final String signature, final String[] exceptions) {
            return INIT.equals(name) ? new InspectConstructor() : null;
        }

        Pair<String, String> key() {
            return ImmutablePair.of(key.getLeft(), key.getRight());
        }

        boolean ignore() {
            return ignore.booleanValue();
        }

        boolean valid() {
            return valid.booleanValue();
        }

        boolean mustRewriteConstructor() {
            return mustRewriteConstructor.booleanValue();
        }
    }

    private static final class Remap extends ClassRemapper {
        private final class RewriteConstructor extends MethodVisitor {
            private RewriteConstructor(final MethodVisitor wrapped) {
                super(ASM_VERSION, wrapped);
            }

            @Override
            public void visitMethodInsn(final int opcode, final String owner, final String name,
                final String desc, final boolean itf) {
                String useDescriptor = desc;
                final ClassWrapper wrapper = wrappers.get(owner);
                if (wrapper != null && wrapper.mustRewriteConstructor) {
                    // simply replace first argument type with OBJECT_TYPE:
                    final Type[] args = Type.getArgumentTypes(desc);
                    args[0] = OBJECT_TYPE;
                    useDescriptor = new Method(INIT, Type.VOID_TYPE, args).getDescriptor();
                }
                super.visitMethodInsn(opcode, owner, name, useDescriptor, itf);
            }
        }

        /**
         * Map of original class to normalized class wrapper.
         */
        final Map<String, ClassWrapper> wrappers;

        private final Map<String, String> classMap;

        private Remap(final ClassVisitor wrapped, final Remapper remapper, final Map<String, String> classMap,
            final Map<String, ClassWrapper> wrappers) {
            super(wrapped, remapper);
            this.classMap = classMap;
            this.wrappers = wrappers;
        }

        @Override
        public void visitInnerClass(final String name, final String outerName, final String innerName,
            final int access) {
            if (!classMap.containsKey(name)) {
                super.visitInnerClass(name, outerName, innerName, access);
            }
        }

        @Override
        @SuppressWarnings("PMD.UseVarargs") //overridden method
        public MethodVisitor visitMethod(final int access, final String name, final String desc,
            final String signature, final String[] exceptions) {
            final MethodVisitor toWrap = super.visitMethod(access, name, desc, signature, exceptions);
            return INIT.equals(name) ? new RewriteConstructor(toWrap) : toWrap;
        }
    }

    /**
     * Marker annotation.
     */
    @Target(ElementType.TYPE)
    private @interface Marker {
    }

    private static class ClassWrapper {
        final Class<?> wrapped;
        final boolean mustRewriteConstructor;

        ClassWrapper(final Class<?> wrapped, final boolean mustRewriteConstructor) {
            this.wrapped = wrapped;
            this.mustRewriteConstructor = mustRewriteConstructor;
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

    private class WriteClass extends ClassVisitor {
        private String className;

        WriteClass() {
            super(ASM_VERSION, new CustomClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
        }

        WriteClass(final ClassReader reader) {
            super(ASM_VERSION, new CustomClassWriter(reader, 0));
        }

        @Override
        @SuppressWarnings("PMD.UseVarargs") //overridden method
        public void visit(final int version, final int access, final String name, final String signature,
            final String superName, final String[] intrfces) {
            super.visit(version, access, name, signature, superName, intrfces);
            className = name;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            final byte[] bytecode = ((ClassWriter) cv).toByteArray();

            final WeaveEnvironment.Resource classfile = env.getClassfile(className);
            env.debug("Writing class %s to %s", className, classfile.getName());
            try (OutputStream outputStream = classfile.getOutputStream()) {
                outputStream.write(bytecode);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private enum IneligibilityReason {
        NOT_ANONYMOUS, TOO_MANY_CONSTRUCTORS, IMPLEMENTS_METHODS, TOO_BUSY_CONSTRUCTOR;
    }

    /**
     * Configuration prefix for this {@link Weaver}.
     */
    public static final String CONFIG_WEAVER = "normalizer.";

    /**
     * Property name referencing a comma-delimited list of types whose subclasses/implementations should be normalized,
     * e.g. {@code javax.enterprise.util.TypeLiteral}.
     */
    public static final String CONFIG_SUPER_TYPES = CONFIG_WEAVER + "superTypes";

    /**
     * Property name referencing a package name to which merged types should be added.
     */
    public static final String CONFIG_TARGET_PACKAGE = CONFIG_WEAVER + "targetPackage";

    /**
     * ASM version in use.
     */
    static final int ASM_VERSION = Opcodes.ASM6;

    /**
     * Method name of a constructor.
     */
    static final String INIT = "<init>";

    /**
     * {@link Type} instance representing {@link Object}.
     */
    static final Type OBJECT_TYPE = Type.getType(Object.class);

    /**
     * {@link WeaveEnvironment} used by this {@link Normalizer} instance.
     */
    final WeaveEnvironment env;

    private final Set<Class<?>> normalizeTypes;
    private final String targetPackage;

    /**
     * Create a new {@link Normalizer} instance.
     * @param env {@link WeaveEnvironment}
     */
    public Normalizer(final WeaveEnvironment env) {
        this.env = env;

        this.targetPackage =
            Utils.validatePackageName(Validate.notBlank(env.config.getProperty(CONFIG_TARGET_PACKAGE),
                "missing target package name"));
        this.normalizeTypes =
            Utils.parseTypes(
                Validate.notEmpty(env.config.getProperty(CONFIG_SUPER_TYPES), "no types specified for normalization"),
                env.classLoader);
    }

    /**
     * Normalize the classes found using the specified {@link Scanner}.
     * @param scanner to scan with
     * @return whether any work was done
     */
    public boolean normalize(final Scanner scanner) {
        boolean result = false;
        for (final Class<?> supertype : normalizeTypes) {
            final Set<Class<?>> subtypes = getBroadlyEligibleSubclasses(supertype, scanner);
            try {
                final Map<Pair<String, String>, Set<ClassWrapper>> segregatedSubtypes = segregate(subtypes);
                for (final Map.Entry<Pair<String, String>, Set<ClassWrapper>> entry : segregatedSubtypes.entrySet()) {
                    final Set<ClassWrapper> likeTypes = entry.getValue();
                    if (likeTypes.size() > 1) {
                        result = true;
                        rewrite(entry.getKey(), likeTypes);
                    }
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return result;
    }

    /**
     * Map a set of classes by their enclosing class.
     * @param sort values
     * @return {@link Map} of enclosing classname to {@link Map} of internal name to {@link ClassWrapper}
     */
    private Map<String, Map<String, ClassWrapper>> byEnclosingClass(final Set<ClassWrapper> sort) {
        final Map<String, Map<String, ClassWrapper>> result = new HashMap<>();
        for (final ClassWrapper wrapper : sort) {
            result.computeIfAbsent(wrapper.wrapped.getEnclosingClass().getName(), k -> new LinkedHashMap<>())
                .put(wrapper.wrapped.getName().replace('.', '/'), wrapper);
        }
        return result;
    }

    /**
     * Rewrite classes as indicated by one entry of {@link #segregate(Iterable)}.
     * @param key {@link String} {@link Pair} indicating supertype and constructor signature
     * @param toMerge matching classes
     * @throws IOException on I/O error
     */
    private void rewrite(final Pair<String, String> key, final Set<ClassWrapper> toMerge) throws IOException {
        final String target = copy(key, toMerge.iterator().next());
        env.info("Merging %s identical %s implementations with constructor %s to type %s", toMerge.size(),
            key.getLeft(), key.getRight(), target);

        final Map<String, Map<String, ClassWrapper>> byEnclosingClass = byEnclosingClass(toMerge);
        for (final Map.Entry<String, Map<String, ClassWrapper>> entry : byEnclosingClass.entrySet()) {
            final String outer = entry.getKey();
            env.debug("Normalizing %s inner classes of %s", entry.getValue().size(), outer);
            final Map<String, String> classMap = new HashMap<>();
            for (final String merged : entry.getValue().keySet()) {
                classMap.put(merged, target);
            }
            final Remapper remapper = new SimpleRemapper(classMap);

            try (InputStream enclosingBytecode = env.getClassfile(outer).getInputStream()) {
                final ClassReader reader = new ClassReader(enclosingBytecode);
                reader.accept(new Remap(new WriteClass(reader), remapper, classMap, entry.getValue()), 0);
            }
            for (final String merged : entry.getValue().keySet()) {
                if (env.deleteClassfile(merged)) {
                    env.debug("Deleted class %s", merged);
                } else {
                    env.warn("Unable to delete class %s", merged);
                }
            }
        }
    }

    /**
     * <p>Find subclasses/implementors of {code supertype} that:
     * <ul>
     * <li>are anonymous</li>
     * <li>declare a single constructor (probably redundant in the case of an anonymous class)</li>
     * <li>do not implement any methods</li>
     * </ul>
     * </p><p>
     * Considered "broadly" eligible because the instructions in the implemented constructor may remove the class from
     * consideration later on.
     * </p>
     * @param supertype whose subtypes are sought
     * @param scanner to use
     * @return {@link Set} of {@link Class}
     * @see #segregate(Iterable)
     */
    private Set<Class<?>> getBroadlyEligibleSubclasses(final Class<?> supertype, final Scanner scanner) {
        final ScanResult scanResult = scanner.scan(new ScanRequest().addSupertypes(supertype));
        final Set<Class<?>> result = new LinkedHashSet<>();
        for (final WeavableClass<?> cls : scanResult.getClasses()) {
            final Class<?> subtype = cls.getTarget();
            final IneligibilityReason reason;
            if (!subtype.isAnonymousClass()) {
                reason = IneligibilityReason.NOT_ANONYMOUS;
            } else if (subtype.getDeclaredConstructors().length != 1) {
                reason = IneligibilityReason.TOO_MANY_CONSTRUCTORS;
            } else if (subtype.getDeclaredMethods().length > 0) {
                reason = IneligibilityReason.IMPLEMENTS_METHODS;
            } else {
                result.add(subtype);
                continue;
            }
            env.debug("Removed %s from consideration due to %s", subtype, reason);
        }
        return result;
    }

    /**
     * <p>Segregate a number of classes (presumed subclasses/implementors of a
     * common supertype/interface). The keys of the map consist of the important
     * parts for identifying similar anonymous types: the "signature" and the
     * invoked superclass constructor. For our purposes, the signature consists
     * of the first applicable item of:
     * <ol>
     * <li>The generic signature of the class</li>
     * <li>The sole implemented interface</li>
     * <li>The superclass</li>
     * </ol>
     * </p><p>
     * The class will be considered ineligible if its constructor is too "busy" as its side effects cannot be
     * anticipated; the normalizer will err on the side of caution.
     * </p><p>
     * Further, we will here avail ourselves of the opportunity to discard any types we have already normalized.
     * </p>
     * @param subtypes
     * @return Map of Pair<String, String> to Set of Classes
     * @throws IOException
     */
    private Map<Pair<String, String>, Set<ClassWrapper>> segregate(final Iterable<Class<?>> subtypes)
        throws IOException {
        final Map<Pair<String, String>, Set<ClassWrapper>> classMap = new LinkedHashMap<>();
        for (final Class<?> subtype : subtypes) {
            final Inspector inspector = new Inspector();
            try (InputStream bytecode = env.getClassfile(subtype).getInputStream()) {
                new ClassReader(bytecode).accept(inspector, 0);
            }
            if (inspector.ignore()) {
                continue;
            }
            if (inspector.valid()) {
                classMap.computeIfAbsent(inspector.key(), k -> new LinkedHashSet<>())
                    .add(new ClassWrapper(subtype, inspector.mustRewriteConstructor()));
            } else {
                env.debug("%s is ineligible for normalization due to %s", subtype,
                    IneligibilityReason.TOO_BUSY_CONSTRUCTOR);
            }
        }
        return classMap;
    }

    /**
     * Create the normalized version of a given class in the configured target package. The {@link Normalizer} will
     * gladly do so in a package from which the normalized class will not actually be able to reference any types upon
     * which it relies; in such a situation you must specify the target package as the package of the supertype.
     * @param key used to generate the normalized classname.
     * @param classWrapper
     * @return the generated classname.
     * @throws IOException
     */
    private String copy(final Pair<String, String> key, final ClassWrapper classWrapper) throws IOException {
        env.debug("Copying %s to %s", key, targetPackage);
        final MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        md5.update(key.getLeft().getBytes(StandardCharsets.UTF_8));
        md5.update(key.getRight().getBytes(StandardCharsets.UTF_8));

        final long digest = Conversion.byteArrayToLong(md5.digest(), 0, 0L, 0, Long.SIZE / Byte.SIZE);

        final String result = MessageFormat.format("{0}/$normalized{1,number,0;_0}", targetPackage, digest);

        env.debug("Copying class %s to %s", classWrapper.wrapped.getName(), result);

        try (InputStream bytecode = env.getClassfile(classWrapper.wrapped).getInputStream()) {
            final ClassReader reader = new ClassReader(bytecode);
            final ClassVisitor writeClass = new WriteClass();

            // we're doing most of this by hand; we only read the original class to hijack signature, ctor exceptions,
            // etc.:

            reader.accept(new ClassVisitor(ASM_VERSION) {
                Type supertype;

                @Override
                @SuppressWarnings("PMD.UseVarargs") //overridden method
                public void visit(final int version, final int access, final String name, final String signature,
                    final String superName, final String[] interfaces) {
                    supertype = Type.getObjectType(superName);
                    writeClass.visit(version, Opcodes.ACC_PUBLIC, result, signature, superName, interfaces);
                    visitAnnotation(Type.getType(Marker.class).getDescriptor(), false);
                }

                @Override
                @SuppressWarnings("PMD.UseVarargs") //overridden method
                public MethodVisitor visitMethod(final int access, final String name, final String desc,
                    final String signature, final String[] exceptions) {
                    if (INIT.equals(name)) {
                        final Method staticCtor = new Method(INIT, key.getRight());
                        final Type[] argumentTypes = staticCtor.getArgumentTypes();
                        final Type[] exceptionTypes = toObjectTypes(exceptions);

                        {
                            final GeneratorAdapter mgen =
                                new GeneratorAdapter(Opcodes.ACC_PUBLIC, staticCtor, signature, exceptionTypes,
                                    writeClass);
                            mgen.visitCode();
                            mgen.loadThis();
                            for (int i = 0; i < argumentTypes.length; i++) {
                                mgen.loadArg(i);
                            }
                            mgen.invokeConstructor(supertype, staticCtor);
                            mgen.returnValue();
                            mgen.endMethod();
                        }
                        /*
                         * now declare a dummy constructor that will match, and discard,
                         * any originally inner-class bound constructor i.e. that set up a this$0 field.
                         * By doing this we can avoid playing with the stack that originally
                         * invoked such a constructor and simply rewrite the method
                         */
                        {
                            final Method instanceCtor =
                                new Method(INIT, Type.VOID_TYPE, ArrayUtils.insert(0, argumentTypes, OBJECT_TYPE));
                            final GeneratorAdapter mgen =
                                new GeneratorAdapter(Opcodes.ACC_PUBLIC, instanceCtor, signature, exceptionTypes,
                                    writeClass);
                            mgen.visitCode();
                            mgen.loadThis();
                            for (int i = 0; i < argumentTypes.length; i++) {
                                mgen.loadArg(i + 1);
                            }
                            mgen.invokeConstructor(supertype, staticCtor);
                            mgen.returnValue();
                            mgen.endMethod();
                        }
                        return null;
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    writeClass.visitEnd();
                }
            }, 0);
        }
        return result;
    }

    /**
     * Translate internal names to Java type names.
     * @param types to translate
     * @return {@link Type}[]
     * @see Type#getObjectType(String)
     */
    @SuppressWarnings("PMD.UseVarargs") //varargs not needed here
    static Type[] toObjectTypes(final String[] types) {
        return types == null ? null : Stream.of(types).map(Type::getObjectType).toArray(Type[]::new);
    }
}
