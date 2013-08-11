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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.xbean.finder.archive.FileArchive;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.SimpleRemapper;

public class Normalizer {
    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    @Target(ElementType.TYPE)
    private @interface Marker {
    }

    private static class ClassWrapper {
        final Class<?> wrapped;
        final boolean mustRewriteConstructor;

        ClassWrapper(Class<?> wrapped, boolean mustRewriteConstructor) {
            this.wrapped = wrapped;
            this.mustRewriteConstructor = mustRewriteConstructor;
        }
    }

    private class WriteClass extends ClassVisitor {
        private String className;

        WriteClass() {
            super(Opcodes.ASM4, new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
        }

        WriteClass(ClassReader reader) {
            super(Opcodes.ASM4, new ClassWriter(reader, 0));
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            final byte[] bytecode = ((ClassWriter) cv).toByteArray();

            final File f = new File(fileArchive.getDir(), className.replace('.', File.separatorChar) + ".class");
            env.debug("Writing class %s to %s", className, f);
            try {
                FileUtils.writeByteArrayToFile(f, bytecode);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private enum IneligibilityReason {
        NOT_ANONYMOUS, TOO_MANY_CONSTRUCTORS, IMPLEMENTS_METHODS, TOO_BUSY_CONSTRUCTOR;
    }

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

    private static final Charset UTF8 = Charset.forName(CharEncoding.UTF_8);

    private final WeaveEnvironment env;
    private final FileArchive fileArchive;

    private final Set<Class<?>> normalizeTypes;
    private final String targetPackage;

    public Normalizer(WeaveEnvironment env) {
        this.env = env;
        fileArchive = new FileArchive(env.classLoader, env.target);

        this.targetPackage =
            Utils.validatePackageName(Validate.notBlank(env.config.getProperty(CONFIG_TARGET_PACKAGE),
                "missing target package name"));
        this.normalizeTypes =
            Utils.parseTypes(
                Validate.notEmpty(env.config.getProperty(CONFIG_SUPER_TYPES), "no types specified for normalization"),
                env.classLoader);
    }

    public boolean normalize(Scanner scanner) {
        for (Class<?> supertype : normalizeTypes) {
            final Set<Class<?>> subtypes = getBroadlyEligibleSubclasses(supertype, scanner);
            try {
                final Map<Pair<String, String>, Set<ClassWrapper>> segregatedSubtypes = segregate(subtypes);
                for (Map.Entry<Pair<String, String>, Set<ClassWrapper>> e : segregatedSubtypes.entrySet()) {
                    rewrite(e.getKey(), e.getValue());
                }
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        }
        return true;
    }

    private Map<String, Map<String, ClassWrapper>> byEnclosingClass(Set<ClassWrapper> sort) {
        final Map<String, Map<String, ClassWrapper>> result = new HashMap<String, Map<String, ClassWrapper>>();
        for (ClassWrapper w : sort) {
            final String outer = w.wrapped.getEnclosingClass().getName();
            Map<String, ClassWrapper> m = result.get(outer);
            if (m == null) {
                m = new LinkedHashMap<String, Normalizer.ClassWrapper>();
                result.put(outer, m);
            }
            m.put(w.wrapped.getName().replace('.', '/'), w);
        }
        return result;
    }

    private void rewrite(Pair<String, String> key, Set<ClassWrapper> toMerge) throws IOException,
        ClassNotFoundException {
        final String target = copy(key, toMerge.iterator().next());
        env.info("Merging %s identical %s implementations with constructor %s to type %s", toMerge.size(),
            key.getLeft(), key.getRight(), target);

        final Map<String, Map<String, ClassWrapper>> byEnclosingClass = byEnclosingClass(toMerge);
        for (final Map.Entry<String, Map<String, ClassWrapper>> e : byEnclosingClass.entrySet()) {
            final String outer = e.getKey();
            env.debug("Normalizing %s inner classes of %s", e.getValue().size(), outer);
            final Map<String, String> classMap = new HashMap<String, String>();
            for (String merged : e.getValue().keySet()) {
                classMap.put(merged, target);
            }
            final Remapper remapper = new SimpleRemapper(classMap);

            final InputStream enclosingBytecode = fileArchive.getBytecode(outer);
            try {
                final ClassReader reader = new ClassReader(enclosingBytecode);

                final ClassVisitor cv = new RemappingClassAdapter(new WriteClass(reader), remapper) {

                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        if (!classMap.containsKey(name)) {
                            super.visitInnerClass(name, outerName, innerName, access);
                        }
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                        final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                        return new MethodVisitor(Opcodes.ASM4, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                                if ("<init>".equals(name)) {
                                    final ClassWrapper w = e.getValue().get(owner);
                                    if (w != null && w.mustRewriteConstructor) {
                                        // simply replace first argument type with OBJECT_TYPE:
                                        final Type[] args = Type.getArgumentTypes(desc);
                                        args[0] = OBJECT_TYPE;
                                        desc = new Method("<init>", Type.VOID_TYPE, args).getDescriptor();
                                    }
                                }
                                super.visitMethodInsn(opcode, owner, name, desc);
                            }
                        };
                    }
                };

                reader.accept(cv, 0);
            } finally {
                IOUtils.closeQuietly(enclosingBytecode);
            }
            for (String merged : e.getValue().keySet()) {
                final File classfile = new File(env.target, merged + ".class");
                if (!classfile.exists()) {
                    env.error("Cannot find classfile for type %s; expected at %s", merged, classfile);
                }
                if (classfile.delete()) {
                    env.debug("Deleted classfile %s", classfile);
                } else {
                    env.warn("Unable to delete classfile %s", classfile);
                }
            }
        }

    }

    /**
     * Find subclasses/implementors of {code supertype} that:
     * <ul>
     * <li>are anonymous</li>
     * <li>declare a single constructor (probably redundant in the case of an anonymous class)</li>
     * <li>do not implement any methods</li>
     * </ul>
     * 
     * Considered "broadly" eligible because the instructions in the implemented constructor may remove the class from
     * consideration later on.
     * 
     * @param supertype
     * @param scanner
     * @return Set of Class
     * @see #segregate(Iterable)
     */
    private Set<Class<?>> getBroadlyEligibleSubclasses(Class<?> supertype, Scanner scanner) {
        final ScanResult scanResult = scanner.scan(new ScanRequest().addSupertypes(supertype));
        final Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        for (WeavableClass<?> w : scanResult.getClasses()) {
            final Class<?> subtype = w.getTarget();
            IneligibilityReason reason = null;
            if (!subtype.isAnonymousClass()) {
                reason = IneligibilityReason.NOT_ANONYMOUS;
            } else if (subtype.getDeclaredConstructors().length != 1) {
                reason = IneligibilityReason.TOO_MANY_CONSTRUCTORS;
            } else if (subtype.getDeclaredMethods().length > 0) {
                reason = IneligibilityReason.IMPLEMENTS_METHODS;
            }
            if (reason == null) {
                result.add(subtype);
            } else {
                env.debug("Removed %s from consideration due to %s", subtype, reason);
            }
        }
        return result;
    }

    /**
     * Segregate a number of classes (presumed subclasses/implementors of a common supertype/interface). The keys of the
     * map consist of the important parts for identifying similar anonymous types: the "signature" and the invoked
     * superclass constructor. For our purposes, the signature consists of the first applicable item of:
     * <ol>
     * <li>The generic signature of the class</li>
     * <li>The sole implemented interface</li>
     * <li>The superclass</li>
     * </ol>
     * 
     * The class will be considered ineligible if its constructor is too "busy" as its side effects cannot be
     * anticipated; the normalizer will err on the side of caution.
     * 
     * Further, we will here avail ourselves of the opportunity to discard any types we have already normalized.
     * 
     * @param subtypes
     * @return Map of ClassKey to Set of Classes
     * @throws Exception
     */
    private Map<Pair<String, String>, Set<ClassWrapper>> segregate(Iterable<Class<?>> subtypes) throws Exception {
        final Map<Pair<String, String>, Set<ClassWrapper>> classMap =
            new LinkedHashMap<Pair<String, String>, Set<ClassWrapper>>();
        for (Class<?> subtype : subtypes) {
            final MutablePair<String, String> key = new MutablePair<String, String>();
            final MutableBoolean valid = new MutableBoolean(true);
            final MutableBoolean mustRewriteConstructor = new MutableBoolean();
            final InputStream bytecode = fileArchive.getBytecode(subtype.getName());

            try {
                new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM4) {
                    String superName;

                    @Override
                    public void visit(int version, int access, String name, String signature, String superName,
                        String[] interfaces) {
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
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (Type.getType(Marker.class).getDescriptor().equals(desc)) {
                            valid.setValue(false);
                        }
                        return null;
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                        if ("<init>".equals(name)) {
                            return new MethodVisitor(Opcodes.ASM4) {
                                public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                                    if ("<init>".equals(name) && owner.equals(superName)) {
                                        key.setRight(desc);
                                    } else {
                                        valid.setValue(false);
                                    }
                                }

                                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                                    if ("this$0".equals(name) && opcode == Opcodes.PUTFIELD) {
                                        mustRewriteConstructor.setValue(true);
                                        return;
                                    }
                                    valid.setValue(false);
                                }
                            };
                        }
                        return null;
                    }
                }, 0);
            } finally {
                IOUtils.closeQuietly(bytecode);
            }
            if (valid.booleanValue()) {
                Set<ClassWrapper> set = classMap.get(key);
                if (set == null) {
                    set = new LinkedHashSet<ClassWrapper>();
                    classMap.put(key, set);
                }
                set.add(new ClassWrapper(subtype, mustRewriteConstructor.booleanValue()));
            } else {
                env.debug("%s is ineligible for normalization due to %s", subtype,
                    IneligibilityReason.TOO_BUSY_CONSTRUCTOR);
            }
        }
        return classMap;
    }

    /**
     * Create the normalized version of a given class in the configured target package. The Normalizer will gladly
     * create this in a package from which the normalized class will not actually be able to reference any types upon
     * which it relies; in such a situation you must specify the target package as the package of the supertype.
     * 
     * @param key used to generate the normalized classname.
     * @param classWrapper
     * @return the generated classname.
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private String copy(final Pair<String, String> key, ClassWrapper classWrapper) throws IOException,
        ClassNotFoundException {
        final MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md5.update(key.getLeft().getBytes(UTF8));
        md5.update(key.getRight().getBytes(UTF8));

        final long digest = Utils.byteArrayToLong(md5.digest(), 0, 0L, 0, Long.SIZE / Byte.SIZE);

        final String result = new StringBuilder(targetPackage).append("/$normalized").append(digest).toString();

        final String className = classWrapper.wrapped.getName();
        env.debug("Copying class %s to %s", className, result);

        final InputStream bytecode = fileArchive.getBytecode(className);

        try {
            final ClassReader reader = new ClassReader(bytecode);

            final ClassVisitor w = new WriteClass();

            // we're doing most of this by hand; we only read the original class to hijack signature, ctor exceptions,
            // etc.:

            reader.accept(new ClassVisitor(Opcodes.ASM4) {
                Type supertype;

                @Override
                public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                    supertype = Type.getObjectType(superName);
                    w.visit(version, Opcodes.ACC_PUBLIC, result, signature, superName, interfaces);

                    visitAnnotation(Type.getType(Marker.class).getDescriptor(), false);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                    if ("<init>".equals(name)) {

                        final Method staticCtor = new Method("<init>", key.getRight());
                        final Type[] argumentTypes = staticCtor.getArgumentTypes();
                        final Type[] exceptionTypes = toObjectTypes(exceptions);

                        {
                            final GeneratorAdapter mg =
                                new GeneratorAdapter(Opcodes.ACC_PUBLIC, staticCtor, signature, exceptionTypes, w);
                            mg.visitCode();
                            mg.loadThis();
                            for (int i = 0; i < argumentTypes.length; i++) {
                                mg.loadArg(i);
                            }
                            mg.invokeConstructor(supertype, staticCtor);
                            mg.returnValue();
                            mg.endMethod();
                        }
                        /*
                         * now declare a dummy constructor that will match, and discard,
                         * any originally inner-class bound constructor i.e. that set up a this$0 field.
                         * By doing this we can avoid playing with the stack that originally
                         * invoked such a constructor and simply rewrite the method
                         */
                        {
                            final Method instanceCtor =
                                new Method("<init>", Type.VOID_TYPE, ArrayUtils.add(argumentTypes, 0, OBJECT_TYPE));
                            final GeneratorAdapter mg =
                                new GeneratorAdapter(Opcodes.ACC_PUBLIC, instanceCtor, signature, exceptionTypes, w);
                            mg.visitCode();
                            mg.loadThis();
                            for (int i = 0; i < argumentTypes.length; i++) {
                                mg.loadArg(i + 1);
                            }
                            mg.invokeConstructor(supertype, staticCtor);
                            mg.returnValue();
                            mg.endMethod();
                        }
                        return null;
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    w.visitEnd();
                }
            }, 0);
        } finally {
            IOUtils.closeQuietly(bytecode);
        }
        return result;
    }

    private Type[] toObjectTypes(String[] types) {
        if (types == null) {
            return null;
        }
        final Type[] result = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = Type.getObjectType(types[i]);
        }
        return result;
    }
}
