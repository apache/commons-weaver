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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.utils.Annotations;
import org.apache.xbean.asm4.AnnotationVisitor;
import org.apache.xbean.asm4.ClassReader;
import org.apache.xbean.asm4.ClassVisitor;
import org.apache.xbean.asm4.FieldVisitor;
import org.apache.xbean.asm4.MethodVisitor;
import org.apache.xbean.asm4.Opcodes;
import org.apache.xbean.asm4.Type;
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
        final Map<String, Object> elements = new LinkedHashMap<String, Object>();

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
        public AnnotationCapturer(final AnnotationVisitor wrapped) {
            super(Opcodes.ASM4, wrapped);
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
            final List<Object> values = new ArrayList<Object>();
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
            classfileAnnotationsFor(info).add(inflate());
        }

        private List<Annotation> classfileAnnotationsFor(final Info info) {
            synchronized (CLASSFILE_ANNOTATIONS) {
                if (!CLASSFILE_ANNOTATIONS.get().containsKey(info)) {
                    final List<Annotation> result = new ArrayList<Annotation>();
                    CLASSFILE_ANNOTATIONS.get().put(info, result);
                    return result;
                }
            }
            return CLASSFILE_ANNOTATIONS.get().get(info);
        }
    }

    /**
     * Specialized {@link ClassVisitor} to inflate annotations for the info
     * objects built by a wrapped {@link InfoBuildingVisitor}.
     */
    public class Visitor extends ClassVisitor {
        private final InfoBuildingVisitor wrapped;

        public Visitor(final InfoBuildingVisitor wrapped) {
            super(Opcodes.ASM4, wrapped);
            this.wrapped = wrapped;
        }

        @Override
        public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
            final Object value) {
            final FieldVisitor toWrap = wrapped.visitField(access, name, desc, signature, value);
            final ClassInfo classInfo = (ClassInfo) wrapped.getInfo();
            FieldInfo testFieldInfo = null;
            // should be the most recently added field, so iterate backward:
            for (int i = classInfo.getFields().size() - 1; i >= 0; i--) {
                final FieldInfo atI = classInfo.getFields().get(i);
                if (atI.getName().equals(name) && atI.getType().equals(desc)) {
                    testFieldInfo = atI;
                    break;
                }
            }
            if (testFieldInfo == null) {
                return toWrap;
            }
            final FieldInfo fieldInfo = testFieldInfo;
            return new FieldVisitor(Opcodes.ASM4, toWrap) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
                    return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, fieldInfo);
                }
            };
        }

        @Override
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
            final Member member;
            try {
                member = compareMethodInfo.get();
                // should be the most recently added method, so iterate backward:
                for (int i = classInfo.getMethods().size() - 1; i >= 0; i--) {
                    final MethodInfo atI = classInfo.getMethods().get(i);
                    if (atI.getName().equals(name) && atI.get().equals(member)) {
                        testMethodInfo = atI;
                        break;
                    }
                }
            } catch (final ClassNotFoundException e) {
                return toWrap;
            }
            if (testMethodInfo == null) {
                return toWrap;
            }
            final MethodInfo methodInfo = testMethodInfo;
            return new MethodVisitor(Opcodes.ASM4, toWrap) {
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
                        try {
                            if (atI.get().getIndex() == param) {
                                parameterInfo = atI;
                                break;
                            }
                        } catch (final ClassNotFoundException e) {
                            continue;
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
        private final T target;
        private final Annotation[] annotations;

        IncludesClassfile(final T target, final List<Annotation> classfileAnnotations) {
            this(target, classfileAnnotations.toArray(new Annotation[classfileAnnotations.size()]));
        }

        IncludesClassfile(final T target, final Annotation[] classfileAnnotations) {
            super();
            this.target = target;
            this.annotations = ArrayUtils.addAll(target.getAnnotations(), classfileAnnotations);
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
            return target;
        }

    }

    /**
     * Helper class for finding elements with annotations (including those with classfile-level retention).
     */
    public final class WithAnnotations {
        private static final String INIT = "<init>";

        private WithAnnotations() {
        }

        public List<Annotated<Package>> findAnnotatedPackages(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedPackages(annotation);
            final List<Annotated<Package>> result = new ArrayList<Annotated<Package>>();
            for (final Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof PackageInfo) {
                    final PackageInfo packageInfo = (PackageInfo) info;
                    try {
                        final IncludesClassfile<Package> annotated =
                            new IncludesClassfile<Package>(packageInfo.get(), classfileAnnotationsFor(packageInfo));
                        if (annotated.isAnnotationPresent(annotation)) {
                            result.add(annotated);
                        }
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                }
            }
            return result;
        }

        public List<Annotated<Class<?>>> findAnnotatedClasses(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedClasses(annotation);
            final List<Annotated<Class<?>>> result = new ArrayList<Annotated<Class<?>>>();
            for (final Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof ClassInfo) {
                    final ClassInfo classInfo = (ClassInfo) info;

                    IncludesClassfile<Class<?>> annotated;
                    try {
                        annotated =
                            new IncludesClassfile<Class<?>>(classInfo.get(), classfileAnnotationsFor(classInfo));
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Class<?>>> findAssignableTypes(final Class<?> supertype) {
            final List<Annotated<Class<?>>> result = new ArrayList<Annotated<Class<?>>>();
            final List<?> assignableTypes;
            if (supertype.isInterface()) {
                assignableTypes = Finder.this.findImplementations(supertype);
            } else {
                assignableTypes = Finder.this.findSubclasses(supertype);
            }

            for (final Object object : assignableTypes) {
                final ClassInfo classInfo = classInfos.get(((Class<?>) object).getName());
                final IncludesClassfile<Class<?>> annotated;
                try {
                    annotated = new IncludesClassfile<Class<?>>(classInfo.get(), classfileAnnotationsFor(classInfo));
                } catch (final ClassNotFoundException e) {
                    continue;
                }
                result.add(annotated);
            }
            return result;
        }

        public List<Annotated<Method>> findAnnotatedMethods(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedMethods(annotation);
            final List<Annotated<Method>> result = new ArrayList<Annotated<Method>>();
            for (final Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof MethodInfo) {
                    final MethodInfo methodInfo = (MethodInfo) info;
                    if (INIT.equals(methodInfo.getName())) {
                        continue;
                    }
                    IncludesClassfile<Method> annotated;
                    try {
                        annotated =
                            new IncludesClassfile<Method>((Method) methodInfo.get(),
                                classfileAnnotationsFor(methodInfo));
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;

        }

        public List<Annotated<Parameter<Method>>> findAnnotatedMethodParameters(
            final Class<? extends Annotation> annotationType) {
            Finder.this.findAnnotatedMethodParameters(annotationType);
            final List<Annotated<Parameter<Method>>> result = new ArrayList<Annotated<Parameter<Method>>>();
            for (final Info info : getAnnotationInfos(annotationType.getName())) {
                if (info instanceof ParameterInfo) {
                    final ParameterInfo parameterInfo = (ParameterInfo) info;
                    if (INIT.equals(parameterInfo.getDeclaringMethod().getName())) {
                        continue;
                    }
                    Parameter<Method> parameter;
                    try {
                        @SuppressWarnings("unchecked")
                        final Parameter<Method> unchecked = (Parameter<Method>) parameterInfo.get();
                        parameter = unchecked;
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                    final IncludesClassfile<Parameter<Method>> annotated =
                        new IncludesClassfile<Parameter<Method>>(parameter, classfileAnnotationsFor(parameterInfo));
                    if (annotated.isAnnotationPresent(annotationType)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Constructor<?>>> findAnnotatedConstructors(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedConstructors(annotation);
            final List<Annotated<Constructor<?>>> result = new ArrayList<Annotated<Constructor<?>>>();
            for (final Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof MethodInfo) {
                    final MethodInfo methodInfo = (MethodInfo) info;
                    if (!INIT.equals(methodInfo.getName())) {
                        continue;
                    }
                    final IncludesClassfile<Constructor<?>> annotated;
                    try {
                        annotated =
                            new IncludesClassfile<Constructor<?>>((Constructor<?>) methodInfo.get(),
                                classfileAnnotationsFor(methodInfo));
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Parameter<Constructor<?>>>> findAnnotatedConstructorParameters(
            final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedConstructorParameters(annotation);
            final List<Annotated<Parameter<Constructor<?>>>> result =
                new ArrayList<Annotated<Parameter<Constructor<?>>>>();
            for (final Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof ParameterInfo) {
                    final ParameterInfo parameterInfo = (ParameterInfo) info;
                    if (!INIT.equals(parameterInfo.getDeclaringMethod().getName())) {
                        continue;
                    }
                    Parameter<Constructor<?>> parameter;
                    try {
                        @SuppressWarnings("unchecked")
                        final Parameter<Constructor<?>> unchecked = (Parameter<Constructor<?>>) parameterInfo.get();
                        parameter = unchecked;
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                    final IncludesClassfile<Parameter<Constructor<?>>> annotated =
                        new IncludesClassfile<Parameter<Constructor<?>>>(parameter,
                            classfileAnnotationsFor(parameterInfo));
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Field>> findAnnotatedFields(final Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedFields(annotation);
            final List<Annotated<Field>> result = new ArrayList<Annotated<Field>>();
            for (final Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof FieldInfo) {
                    final FieldInfo fieldInfo = (FieldInfo) info;
                    try {
                        final IncludesClassfile<Field> annotated =
                            new IncludesClassfile<Field>((Field) fieldInfo.get(), classfileAnnotationsFor(fieldInfo));
                        if (annotated.isAnnotationPresent(annotation)) {
                            result.add(annotated);
                        }
                    } catch (final ClassNotFoundException e) {
                        continue;
                    }
                }
            }
            return result;
        }

        private List<Annotation> classfileAnnotationsFor(final Info info) {
            synchronized (classfileAnnotations) {
                if (!classfileAnnotations.containsKey(info)) {
                    final List<Annotation> result = new ArrayList<Annotation>();
                    classfileAnnotations.put(info, result);
                    return result;
                }
            }
            return classfileAnnotations.get(info);
        }

    }

    private static final int ASM_FLAGS = ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES;

    /**
     * The {@link #classfileAnnotations} member stores these; however the scanning takes place in the scope of the super
     * constructor call, thus there is no opportunity to set the reference beforehand. To work around this, we use a
     * static ThreadLocal with an initializer and pull/clear its value when we return from the super constructor. :P
     */
    private static final ThreadLocal<Map<Info, List<Annotation>>> CLASSFILE_ANNOTATIONS =
        new ThreadLocal<Map<Info, List<Annotation>>>() {
            @Override
            protected Map<Info, List<Annotation>> initialValue() {
                return new IdentityHashMap<AnnotationFinder.Info, List<Annotation>>();
            }
        };

    private final WithAnnotations withAnnotations = new WithAnnotations();
    private final Map<Info, List<Annotation>> classfileAnnotations;
    private final Inflater inflater;

    /**
     * Create a new {@link Finder} instance.
     * @param archive
     */
    public Finder(final Archive archive) {
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
            final ClassReader classReader = new ClassReader(bytecode);
            classReader.accept(new Visitor(new InfoBuildingVisitor()), ASM_FLAGS);
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

        for (final WeaveInterest interest : request.getInterests()) {
            switch (interest.target) {
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
                for (final Annotated<Field> fld : this.withAnnotations().findAnnotatedFields(interest.annotationType)) {
                    result.getWeavable(fld.get()).addAnnotations(fld.getAnnotations());
                }
                break;
            case PARAMETER:
                for (final Annotated<Parameter<Method>> parameter : this.withAnnotations()
                    .findAnnotatedMethodParameters(interest.annotationType)) {
                    result.getWeavable(parameter.get().getDeclaringExecutable())
                        .getWeavableParameter(parameter.get().getIndex()).addAnnotations(parameter.getAnnotations());
                }
                for (final Annotated<Parameter<Constructor<?>>> parameter : this.withAnnotations()
                    .findAnnotatedConstructorParameters(interest.annotationType)) {
                    result.getWeavable(parameter.get().getDeclaringExecutable())
                        .getWeavableParameter(parameter.get().getIndex()).addAnnotations(parameter.getAnnotations());
                }
                break;
            default:
                // should we log something?
                break;
            }
        }
        for (final Class<?> supertype : request.getSupertypes()) {
            for (final Annotated<Class<?>> type : this.withAnnotations().findAssignableTypes(supertype)) {
                result.getWeavable(type.get()).addAnnotations(type.getAnnotations());
            }
        }
        return inflater.inflate(result);
    }

    private Class<?> toClass(final Type type) {
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
