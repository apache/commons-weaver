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
package org.apache.commons.weaver;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.utils.Annotations;
import org.apache.xbean.asm6.AnnotationVisitor;
import org.apache.xbean.asm6.ClassReader;
import org.apache.xbean.asm6.ClassVisitor;
import org.apache.xbean.asm6.FieldVisitor;
import org.apache.xbean.asm6.MethodVisitor;
import org.apache.xbean.asm6.Opcodes;
import org.apache.xbean.asm6.Type;
import org.apache.xbean.finder.Annotated;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.Parameter;
import org.apache.xbean.finder.archive.Archive;

/**
 * Scanner implementation.
 */
class Finder extends AnnotationFinder implements Scanner {

    private abstract class AnnotationInflater extends AnnotationCapturer {
        final Class<? extends Annotation> annotationType;
        final Map<String, Object> elements = new LinkedHashMap<>();

        AnnotationInflater(final String desc, final AnnotationVisitor wrapped) {
            super(wrapped);
            this.annotationType = toClass(Type.getType(desc)).asSubclass(Annotation.class);
        }

        Annotation inflate() {
            return Annotations.instanceOf(annotationType, elements);
        }

        @Override
        protected void storeValue(final String name, final Object value) {
            Object toStore = value;
            Validate.notNull(toStore, "null annotation element");
            if (toStore.getClass().isArray()) {
                final Class<?> requiredType;
                try {
                    requiredType = annotationType.getDeclaredMethod(name).getReturnType();
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
                if (!requiredType.isInstance(toStore)) {
                    final int len = Array.getLength(toStore);
                    final Object typedArray = Array.newInstance(requiredType.getComponentType(), len);
                    for (int i = 0; i < len; i++) {
                        Object element = Array.get(toStore, i);
                        if (element instanceof Type) {
                            element = toClass((Type) element);
                        }
                        Array.set(typedArray, i, element);
                    }
                    toStore = typedArray;
                }
            } else if (toStore instanceof Type) {
                toStore = toClass((Type) toStore);
            }
            elements.put(name, toStore);
        }
    }

    private abstract class AnnotationCapturer extends AnnotationVisitor {
        AnnotationCapturer(final AnnotationVisitor wrapped) {
            super(ASM_VERSION, wrapped);
        }

        /**
         * Template method for storing an annotation value.
         * @param name
         * @param value
         */
        protected abstract void storeValue(String name, Object value);

        @Override
        public void visit(final String name, final Object value) {
            storeValue(name, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String name, final String desc) {
            final AnnotationCapturer owner = this;
            return new AnnotationInflater(desc, super.visitAnnotation(name, desc)) {

                @Override
                public void visitEnd() {
                    owner.storeValue(name, inflate());
                }
            };
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            final AnnotationCapturer owner = this;
            final List<Object> values = new ArrayList<>();
            return new AnnotationCapturer(super.visitArray(name)) {

                @Override
                public void visitEnd() {
                    owner.storeValue(name, values.toArray());
                    super.visitEnd();
                }

                @Override
                protected void storeValue(final String name, final Object value) {
                    values.add(value);
                }
            };
        }

        @Override
        public void visitEnum(final String name, final String desc, final String value) {
            super.visitEnum(name, desc, value);
            @SuppressWarnings("rawtypes")
            final Class<? extends Enum> enumType;
            try {
                enumType = Class.forName(Type.getType(desc).getClassName()).asSubclass(Enum.class);
            } catch (final ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            @SuppressWarnings("unchecked")
            final Enum<?> enumValue = Enum.valueOf(enumType, value);
            storeValue(name, enumValue);
        }

    }

    private class TopLevelAnnotationInflater extends AnnotationInflater {
        private final Info info;

        TopLevelAnnotationInflater(final String desc, final AnnotationVisitor wrapped, final Info info) {
            super(desc, wrapped);
            this.info = info;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            CLASSFILE_ANNOTATIONS.get().computeIfAbsent(info, k -> new ArrayList<>()).add(inflate());
        }
    }

    /**
     * Specialized {@link ClassVisitor} to inflate annotations for the info
     * objects built by a wrapped {@link InfoBuildingVisitor}.
     */
    public class Visitor extends ClassVisitor {
        private final InfoBuildingVisitor wrapped;

        Visitor(final InfoBuildingVisitor wrapped) {
            super(ASM_VERSION, wrapped);
            this.wrapped = wrapped;
        }

        @Override
        public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
            final Object value) {
            final FieldVisitor toWrap = wrapped.visitField(access, name, desc, signature, value);
            final ClassInfo classInfo = (ClassInfo) wrapped.getInfo();
            final Type fieldType = Type.getType(desc);
            FieldInfo testFieldInfo = null;
            // should be the most recently added field, so iterate backward:
            for (int i = classInfo.getFields().size() - 1; i >= 0; i--) {
                final FieldInfo atI = classInfo.getFields().get(i);
                if (atI.getName().equals(name)) {
                    final String type = atI.getType();
                    if (StringUtils.equals(type, fieldType.getClassName())
                        || StringUtils.equals(type, fieldType.getDescriptor())) {
                        testFieldInfo = atI;
                        break;
                    }
                }
            }
            if (testFieldInfo == null) {
                return toWrap;
            }
            final FieldInfo fieldInfo = testFieldInfo;
            return new FieldVisitor(ASM_VERSION, toWrap) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
                    return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, fieldInfo);
                }
            };
        }

        @Override
        @SuppressWarnings("PMD.UseVarargs") // overridden method
        public MethodVisitor visitMethod(final int access, final String name, final String desc,
            final String signature, final String[] exceptions) {
            final MethodVisitor toWrap = wrapped.visitMethod(access, name, desc, signature, exceptions);
            final ClassInfo classInfo = (ClassInfo) wrapped.getInfo();

            // MethodInfo may not always come from a descriptor, so we must go by the
            // Member represented. Make sure the method either has a valid name or is a constructor:
            final MethodInfo compareMethodInfo = new MethodInfo(classInfo, name, desc);
            if (!compareMethodInfo.isConstructor() && !isJavaIdentifier(name)) {
                return toWrap;
            }
            MethodInfo testMethodInfo = null;
            // should be the most recently added method, so iterate backward:
            for (int i = classInfo.getMethods().size() - 1; i >= 0; i--) {
                final MethodInfo atI = classInfo.getMethods().get(i);
                if (atI.getName().equals(name) && StringUtils.equals(atI.getDescriptor(), desc)) {
                    testMethodInfo = atI;
                    break;
                }
            }
            if (testMethodInfo == null) {
                return toWrap;
            }
            final MethodInfo methodInfo = testMethodInfo;
            return new MethodVisitor(ASM_VERSION, toWrap) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
                    return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, methodInfo);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(final int param, final String desc,
                    final boolean visible) {
                    final AnnotationVisitor toWrap = super.visitParameterAnnotation(param, desc, visible);
                    if (visible) {
                        return toWrap;
                    }
                    ParameterInfo parameterInfo = null;

                    // should be the most recently added parameter, so iterate backward:
                    for (int i = methodInfo.getParameters().size() - 1; i >= 0; i--) {
                        final ParameterInfo atI = methodInfo.getParameters().get(i);
                        if (atI.getName().equals(Integer.toString(param))) {
                            parameterInfo = atI;
                            break;
                        }
                    }
                    return parameterInfo == null ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, parameterInfo);
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
            return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, wrapped.getInfo());
        }

        private boolean isJavaIdentifier(final String toCheck) {
            if (toCheck.isEmpty() || !Character.isJavaIdentifierStart(toCheck.charAt(0))) {
                return false;
            }
            for (final char chr : toCheck.substring(1).toCharArray()) {
                if (!Character.isJavaIdentifierPart(chr)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class IncludesClassfile<T extends AnnotatedElement> implements Annotated<T> {
        private final T annotatedElement;
        private final Annotation[] annotations;

        IncludesClassfile(final T annotatedElement, final List<Annotation> classfileAnnotations) {
            this(annotatedElement, classfileAnnotations.toArray(new Annotation[0]));
        }

        @SuppressWarnings("PMD.UseVarargs") // varargs not necessary here
        IncludesClassfile(final T annotatedElement, final Annotation[] classfileAnnotations) {
            super();
            this.annotatedElement = annotatedElement;
            this.annotations = ArrayUtils.addAll(annotatedElement.getAnnotations(), classfileAnnotations);
        }

        @Override
        public <A extends Annotation> A getAnnotation(final Class<A> annotationType) {
            for (final Annotation prospect : annotations) {
                if (prospect.annotationType().equals(annotationType)) {
                    @SuppressWarnings("unchecked")
                    final A result = (A) prospect;
                    return result;
                }
            }
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            final Annotation[] result = new Annotation[annotations.length];
            System.arraycopy(annotations, 0, result, 0, annotations.length);
            return result;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return getAnnotations();
        }

        @Override
        public boolean isAnnotationPresent(final Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Override
        public T get() {
            return annotatedElement;
        }
    }

    /**
     * Helper class for finding elements with annotations (including those with classfile-level retention).
     */
    public final class WithAnnotations {
        private WithAnnotations() {
        }

        public List<Annotated<Package>> findAnnotatedPackages(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedPackages(annotation);

            return typed(PackageInfo.class, getAnnotationInfos(annotation.getName())::stream).map(packageInfo -> {
                try {
                    return new IncludesClassfile<>(packageInfo.get(), classfileAnnotationsFor(packageInfo));
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }).filter(hasAnnotation(annotation)).collect(Collectors.toList());
        }

        /**
         * Get the list of objects representing all scanned classes.
         * @since 1.3
         * @return {@link List} of {@link Annotated}{@code <Class<?>>}
         */
        public List<Annotated<Class<?>>> getAllClasses() {
            return annotate(originalInfos.values());
        }

        public List<Annotated<Class<?>>> findAnnotatedClasses(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedClasses(annotation);

            return annotate(getAnnotationInfos(annotation.getName())).stream().filter(hasAnnotation(annotation))
                .collect(Collectors.toList());
        }

        private List<Annotated<Class<?>>> annotate(final Collection<? extends Info> infos) {
            return typed(ClassInfo.class, infos::stream).map(classInfo -> {
                try {
                    return new IncludesClassfile<Class<?>>(classInfo.get(), classfileAnnotationsFor(classInfo));
                } catch (ClassNotFoundException e1) {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }

        public List<Annotated<Class<?>>> findAssignableTypes(final Class<?> supertype) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            final List<Class<?>> assignableTypes = (List) (supertype.isInterface()
                ? Finder.this.findImplementations(supertype) : Finder.this.findSubclasses(supertype));

            return assignableTypes.stream().map(Class::getName).map(classInfos::get)
                .<IncludesClassfile<Class<?>>> map(classInfo -> {
                    try {
                        return new IncludesClassfile<>(classInfo.get(), classfileAnnotationsFor(classInfo));
                    } catch (final ClassNotFoundException e) {
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList());
        }

        public List<Annotated<Method>> findAnnotatedMethods(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedMethods(annotation);

            return typed(MethodInfo.class, getAnnotationInfos(annotation.getName())::stream).filter(CTOR.negate())
                .map(methodInfo -> {
                    try {
                        return new IncludesClassfile<>((Method) methodInfo.get(),
                            classfileAnnotationsFor(methodInfo));
                    } catch (final ClassNotFoundException e) {
                        return null;
                    }
                }).filter(hasAnnotation(annotation)).collect(Collectors.toList());
        }

        public List<Annotated<Parameter<Method>>> findAnnotatedMethodParameters(
            final Class<? extends Annotation> annotationType) {
            Finder.this.findAnnotatedMethodParameters(annotationType);

            return typed(ParameterInfo.class, getAnnotationInfos(annotationType.getName())::stream)
                .filter(isCtor(ParameterInfo::getDeclaringMethod).negate()).map(parameterInfo -> {
                    try {
                        @SuppressWarnings("unchecked")
                        final Parameter<Method> parameter = (Parameter<Method>) parameterInfo.get();
                        return new IncludesClassfile<>(parameter, classfileAnnotationsFor(parameterInfo));
                    } catch (final ClassNotFoundException e) {
                        return null;
                    }
                }).filter(hasAnnotation(annotationType)).collect(Collectors.toList());
        }

        public List<Annotated<Constructor<?>>> findAnnotatedConstructors(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedConstructors(annotation);

            return typed(MethodInfo.class, getAnnotationInfos(annotation.getName())::stream).filter(CTOR)
                .map(methodInfo -> {
                    try {
                        final IncludesClassfile<Constructor<?>> annotated = new IncludesClassfile<>(
                            (Constructor<?>) methodInfo.get(), classfileAnnotationsFor(methodInfo));
                        return annotated;
                    } catch (final ClassNotFoundException e) {
                        return null;
                    }
                }).filter(hasAnnotation(annotation)).collect(Collectors.toList());
        }

        public List<Annotated<Parameter<Constructor<?>>>> findAnnotatedConstructorParameters(
            final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedConstructorParameters(annotation);

            return typed(ParameterInfo.class, getAnnotationInfos(annotation.getName())::stream)
                .filter(isCtor(ParameterInfo::getDeclaringMethod)).map(parameterInfo -> {
                    try {
                        @SuppressWarnings("unchecked")
                        final Parameter<Constructor<?>> parameter = (Parameter<Constructor<?>>) parameterInfo.get();
                        return new IncludesClassfile<>(parameter, classfileAnnotationsFor(parameterInfo));
                    } catch (final ClassNotFoundException e) {
                        return null;
                    }
                }).filter(hasAnnotation(annotation)).collect(Collectors.toList());
        }

        public List<Annotated<Field>> findAnnotatedFields(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedFields(annotation);

            return typed(FieldInfo.class, getAnnotationInfos(annotation.getName())::stream).map(fieldInfo -> {
                try {
                    return new IncludesClassfile<>((Field) fieldInfo.get(), classfileAnnotationsFor(fieldInfo));
                } catch (final ClassNotFoundException e) {
                    return null;
                }
            }).filter(hasAnnotation(annotation)).collect(Collectors.toList());
        }

        private List<Annotation> classfileAnnotationsFor(final Info info) {
            return classfileAnnotations.computeIfAbsent(info, k -> new ArrayList<>());
        }
    }

    private static final int ASM_FLAGS = ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES;

    private static final String INIT = "<init>";

    /**
     * ASM version in use.
     */
    static final int ASM_VERSION = Opcodes.ASM6;

    /**
     * Ctor {@link Predicate}.
     */
    static final Predicate<MethodInfo> CTOR = methodInfo -> INIT.equals(methodInfo.getName());

    /**
     * The {@link #classfileAnnotations} member stores these; however the scanning takes place in the scope of the super
     * constructor call, thus there is no opportunity to set the reference beforehand. To work around this, we use a
     * static ThreadLocal with an initializer and pull/clear its value when we return from the super constructor. :P
     */
    static final ThreadLocal<Map<Info, List<Annotation>>> CLASSFILE_ANNOTATIONS =
        ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * Filter and cast {@code stream}.
     * @param type
     * @param stream
     * @return {@link Stream}
     */
    static <T, U> Stream<U> typed(final Class<U> type, final Supplier<Stream<T>> stream) {
        return stream.get().filter(type::isInstance).map(type::cast);
    }

    /**
     * Obtain a {@link Predicate} to test whether an {@link Annotated} instance
     * hosts annotations of the specified type.
     *
     * @param annotation
     * @return {@link Predicate}
     */
    static Predicate<Annotated<?>> hasAnnotation(final Class<? extends Annotation> annotation) {
        return annotated -> annotated != null && annotated.isAnnotationPresent(annotation);
    }

    /**
     * Obtain a {@link Predicate} to test whether an argument, once transformed
     * by the specified {@link Function}, represents a Java constructor.
     *
     * @param xform
     * @return {@link Predicate}
     */
    static <T> Predicate<T> isCtor(final Function<? super T, MethodInfo> xform) {
        return t -> CTOR.test(xform.apply(t));
    }

    /**
     * Map of {@link Info} to {@link List} of classfile {@link Annotation}s.
     */
    final Map<Info, List<Annotation>> classfileAnnotations;

    private final WithAnnotations withAnnotations = new WithAnnotations();
    private final Inflater inflater;

    /**
     * Create a new {@link Finder} instance.
     * @param archive
     */
    Finder(final Archive archive) {
        super(archive, false);
        classfileAnnotations = CLASSFILE_ANNOTATIONS.get();
        CLASSFILE_ANNOTATIONS.remove();
        inflater = new Inflater(classfileAnnotations);
        enableFindImplementations();
        enableFindSubclasses();
    }

    /**
     * Fluent "finder with annotations".
     * @return {@link WithAnnotations}
     */
    public WithAnnotations withAnnotations() {
        return withAnnotations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readClassDef(final InputStream bytecode) throws IOException {
        try {
            new ClassReader(bytecode).accept(new Visitor(new InfoBuildingVisitor()), ASM_FLAGS);
        } finally {
            bytecode.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationFinder select(final Class<?>... arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationFinder select(final Iterable<String> clazz) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationFinder select(final String... clazz) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScanResult scan(final ScanRequest request) {
        final ScanResult result = new ScanResult();

        if (!request.isConstrained() || request.getSupertypes().contains(Object.class)) {
            for (final Annotated<Class<?>> type : this.withAnnotations().getAllClasses()) {
                result.getWeavable(type.get()).addAnnotations(type.getAnnotations());
            }
        } else {
            for (final WeaveInterest interest : request.getInterests()) {
                final ElementType target = interest.target;
                switch (target) {
                case PACKAGE:
                    for (final Annotated<Package> pkg : this.withAnnotations().findAnnotatedPackages(
                        interest.annotationType)) {
                        result.getWeavable(pkg.get()).addAnnotations(pkg.getAnnotations());
                    }
                    break;
                case TYPE:
                    for (final Annotated<Class<?>> type : this.withAnnotations().findAnnotatedClasses(
                        interest.annotationType)) {
                        result.getWeavable(type.get()).addAnnotations(type.getAnnotations());
                    }
                    break;
                case METHOD:
                    for (final Annotated<Method> method : this.withAnnotations().findAnnotatedMethods(
                        interest.annotationType)) {
                        result.getWeavable(method.get()).addAnnotations(method.getAnnotations());
                    }
                    break;
                case CONSTRUCTOR:
                    for (final Annotated<Constructor<?>> ctor : this.withAnnotations().findAnnotatedConstructors(
                        interest.annotationType)) {
                        result.getWeavable(ctor.get()).addAnnotations(ctor.getAnnotations());
                    }
                    break;
                case FIELD:
                        for (final Annotated<Field> fld : this.withAnnotations()
                            .findAnnotatedFields(interest.annotationType)) {
                            result.getWeavable(fld.get()).addAnnotations(fld.getAnnotations());
                        }
                        break;
                case PARAMETER:
                    for (final Annotated<Parameter<Method>> parameter : this.withAnnotations()
                        .findAnnotatedMethodParameters(interest.annotationType)) {
                            result.getWeavable(parameter.get().getDeclaringExecutable())
                                .getWeavableParameter(parameter.get().getIndex())
                                .addAnnotations(parameter.getAnnotations());
                        }
                    for (final Annotated<Parameter<Constructor<?>>> parameter : this.withAnnotations()
                        .findAnnotatedConstructorParameters(interest.annotationType)) {
                            result.getWeavable(parameter.get().getDeclaringExecutable())
                                .getWeavableParameter(parameter.get().getIndex())
                                .addAnnotations(parameter.getAnnotations());
                        }
                    break;
                default:
                    // should we log something?
                    break;
                }
            }
            request.getSupertypes().stream().map(this.withAnnotations()::findAssignableTypes)
                .flatMap(Collection::stream)
                .forEach(type -> result.getWeavable(type.get()).addAnnotations(type.getAnnotations()));
        }
        return inflater.inflate(result);
    }

    /**
     * Transform a {@link java.lang.reflect.Type} instance to a {@link Class}.
     * @param type
     * @return {@link Class}
     */
    Class<?> toClass(final Type type) {
        final String className;
        if (type.getSort() == Type.ARRAY) {
            className = type.getElementType().getClassName();
        } else {
            className = type.getClassName();
        }
        Class<?> result;
        try {
            result = Class.forName(className);
        } catch (final ClassNotFoundException e) {
            try {
                result = getArchive().loadClass(className);
            } catch (final ClassNotFoundException e1) {
                throw new RuntimeException(e1);
            }
        }
        if (type.getSort() == Type.ARRAY) {
            final int[] dims = new int[type.getDimensions()];
            Arrays.fill(dims, 0);
            result = Array.newInstance(result, dims).getClass();
        }
        return result;
    }
}
