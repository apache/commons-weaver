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

        AnnotationInflater(String desc, AnnotationVisitor wrapped) {
            super(wrapped);
            this.annotationType = toClass(Type.getType(desc)).asSubclass(Annotation.class);
        }

        Annotation inflate() {
            return Annotations.instanceOf(annotationType, elements);
        }

        @Override
        protected void storeValue(String name, Object value) {
            Validate.notNull(value, "null annotation element");
            if (value.getClass().isArray()) {
                final Class<?> requiredType;
                try {
                    requiredType = annotationType.getDeclaredMethod(name).getReturnType();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (!requiredType.isInstance(value)) {
                    final int len = Array.getLength(value);
                    final Object typedArray = Array.newInstance(requiredType.getComponentType(), len);
                    for (int i = 0; i < len; i++) {
                        Object o = Array.get(value, i);
                        if (o instanceof Type) {
                            o = toClass((Type) o);
                        }
                        Array.set(typedArray, i, o);
                    }
                    value = typedArray;
                }
            } else if (value instanceof Type) {
                value = toClass((Type) value);
            }
            elements.put(name, value);
        }
    }

    private abstract class AnnotationCapturer extends AnnotationVisitor {
        public AnnotationCapturer(AnnotationVisitor wrapped) {
            super(Opcodes.ASM4, wrapped);
        }

        protected abstract void storeValue(String name, Object value);

        @Override
        public void visit(String name, Object value) {
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
                protected void storeValue(String name, Object value) {
                    values.add(value);
                }
            };
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            super.visitEnum(name, desc, value);
            @SuppressWarnings("rawtypes")
            final Class<? extends Enum> enumType;
            try {
                enumType = Class.forName(Type.getType(desc).getClassName()).asSubclass(Enum.class);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            @SuppressWarnings("unchecked")
            final Enum<?> e = Enum.valueOf(enumType, value);
            storeValue(name, e);
        }

    }

    private class TopLevelAnnotationInflater extends AnnotationInflater {
        private final Info info;

        TopLevelAnnotationInflater(String desc, AnnotationVisitor wrapped, Info info) {
            super(desc, wrapped);
            this.info = info;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            classfileAnnotationsFor(info).add(inflate());
        }

        private List<Annotation> classfileAnnotationsFor(Info info) {
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

    public class Visitor extends ClassVisitor {
        private final InfoBuildingVisitor wrapped;

        public Visitor(InfoBuildingVisitor wrapped) {
            super(Opcodes.ASM4, wrapped);
            this.wrapped = wrapped;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
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
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
                    return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, fieldInfo);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            final MethodVisitor toWrap = wrapped.visitMethod(access, name, desc, signature, exceptions);
            final ClassInfo classInfo = (ClassInfo) wrapped.getInfo();

            // MethodInfo may not always come from a descriptor, so we must go by the
            // Member represented. Make sure the method either has a valid name or is a constructor:
            final MethodInfo compareMethodInfo = new MethodInfo(classInfo, name, desc);
            if (!compareMethodInfo.isConstructor() && !isJavaIdentifier(name)) {
                return toWrap;
            }
            MethodInfo testMethodInfo = null;
            final Member m;
            try {
                m = compareMethodInfo.get();
                // should be the most recently added method, so iterate backward:
                for (int i = classInfo.getMethods().size() - 1; i >= 0; i--) {
                    final MethodInfo atI = classInfo.getMethods().get(i);
                    if (atI.getName().equals(name) && atI.get().equals(m)) {
                        testMethodInfo = atI;
                        break;
                    }
                }
            } catch (ClassNotFoundException e) {
            }
            if (testMethodInfo == null) {
                return toWrap;
            }
            final MethodInfo methodInfo = testMethodInfo;
            return new MethodVisitor(Opcodes.ASM4, toWrap) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
                    return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, methodInfo);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int param, String desc, boolean visible) {
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
                        } catch (ClassNotFoundException e) {

                        }
                    }
                    return parameterInfo == null ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, parameterInfo);
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            final AnnotationVisitor toWrap = super.visitAnnotation(desc, visible);
            return visible ? toWrap : new TopLevelAnnotationInflater(desc, toWrap, wrapped.getInfo());
        }

        private boolean isJavaIdentifier(String s) {
            if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
                return false;
            }
            for (int i = 1, sz = s.length(); i < sz; i++) {
                if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class IncludesClassfile<T extends AnnotatedElement> implements Annotated<T> {
        private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

        private final T target;
        private final Annotation[] annotations;

        IncludesClassfile(T target, List<Annotation> classfileAnnotations) {
            this(target, classfileAnnotations.toArray(EMPTY_ANNOTATION_ARRAY));
        }

        IncludesClassfile(T target, Annotation[] classfileAnnotations) {
            super();
            this.target = target;

            final Annotation[] runtime = target.getAnnotations();
            if (classfileAnnotations == null || classfileAnnotations.length == 0) {
                annotations = runtime;
            } else {
                annotations = new Annotation[runtime.length + classfileAnnotations.length];
                System.arraycopy(runtime, 0, annotations, 0, runtime.length);
                System.arraycopy(classfileAnnotations, 0, annotations, runtime.length, classfileAnnotations.length);
            }
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            for (Annotation prospect : annotations) {
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
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
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
    public class WithAnnotations {
        private WithAnnotations() {
        }

        public List<Annotated<Package>> findAnnotatedPackages(Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedPackages(annotation);
            final List<Annotated<Package>> result = new ArrayList<Annotated<Package>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof PackageInfo) {
                    PackageInfo packageInfo = (PackageInfo) info;
                    try {
                        IncludesClassfile<Package> annotated =
                            new IncludesClassfile<Package>(packageInfo.get(), classfileAnnotationsFor(packageInfo));
                        if (annotated.isAnnotationPresent(annotation)) {
                            result.add(annotated);
                        }
                    } catch (ClassNotFoundException e) {
                    }
                }
            }
            return result;
        }

        public List<Annotated<Class<?>>> findAnnotatedClasses(Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedClasses(annotation);
            final List<Annotated<Class<?>>> result = new ArrayList<Annotated<Class<?>>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof ClassInfo) {
                    ClassInfo classInfo = (ClassInfo) info;

                    IncludesClassfile<Class<?>> annotated;
                    try {
                        annotated =
                            new IncludesClassfile<Class<?>>(classInfo.get(), classfileAnnotationsFor(classInfo));
                    } catch (ClassNotFoundException e) {
                        continue;
                    }
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Class<?>>> findAssignableTypes(Class<?> supertype) {
            final List<Annotated<Class<?>>> result = new ArrayList<Annotated<Class<?>>>();
            final List<?> assignableTypes;
            if (supertype.isInterface()) {
                assignableTypes = Finder.this.findImplementations(supertype);
            } else {
                assignableTypes = Finder.this.findSubclasses(supertype);
            }

            for (Object object : assignableTypes) {
                final ClassInfo classInfo = classInfos.get(((Class<?>) object).getName());
                final IncludesClassfile<Class<?>> annotated;
                try {
                    annotated = new IncludesClassfile<Class<?>>(classInfo.get(), classfileAnnotationsFor(classInfo));
                } catch (ClassNotFoundException e) {
                    continue;
                }
                result.add(annotated);
            }
            return result;
        }

        public List<Annotated<Method>> findAnnotatedMethods(Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedMethods(annotation);
            final List<Annotated<Method>> result = new ArrayList<Annotated<Method>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof MethodInfo) {
                    MethodInfo methodInfo = (MethodInfo) info;
                    if ("<init>".equals(methodInfo.getName())) {
                        continue;
                    }
                    IncludesClassfile<Method> annotated;
                    try {
                        annotated =
                            new IncludesClassfile<Method>((Method) methodInfo.get(),
                                classfileAnnotationsFor(methodInfo));
                    } catch (ClassNotFoundException e) {
                        continue;
                    }
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;

        }

        public List<Annotated<Parameter<Method>>> findAnnotatedMethodParameters(Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedMethodParameters(annotation);
            final List<Annotated<Parameter<Method>>> result = new ArrayList<Annotated<Parameter<Method>>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof ParameterInfo) {
                    ParameterInfo parameterInfo = (ParameterInfo) info;
                    if ("<init>".equals(parameterInfo.getDeclaringMethod().getName())) {
                        continue;
                    }
                    Parameter<Method> parameter;
                    try {
                        @SuppressWarnings("unchecked")
                        Parameter<Method> unchecked = (Parameter<Method>) parameterInfo.get();
                        parameter = unchecked;
                    } catch (ClassNotFoundException e) {
                        continue;
                    }
                    IncludesClassfile<Parameter<Method>> annotated =
                        new IncludesClassfile<Parameter<Method>>(parameter, classfileAnnotationsFor(parameterInfo));
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Constructor<?>>> findAnnotatedConstructors(Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedConstructors(annotation);
            final List<Annotated<Constructor<?>>> result = new ArrayList<Annotated<Constructor<?>>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof MethodInfo) {
                    MethodInfo methodInfo = (MethodInfo) info;
                    if (!"<init>".equals(methodInfo.getName())) {
                        continue;
                    }
                    IncludesClassfile<Constructor<?>> annotated;
                    try {
                        annotated =
                            new IncludesClassfile<Constructor<?>>((Constructor<?>) methodInfo.get(),
                                classfileAnnotationsFor(methodInfo));
                    } catch (ClassNotFoundException e) {
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
            Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedConstructorParameters(annotation);
            final List<Annotated<Parameter<Constructor<?>>>> result =
                new ArrayList<Annotated<Parameter<Constructor<?>>>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof ParameterInfo) {
                    ParameterInfo parameterInfo = (ParameterInfo) info;
                    if (!"<init>".equals(parameterInfo.getDeclaringMethod().getName())) {
                        continue;
                    }
                    Parameter<Constructor<?>> parameter;
                    try {
                        @SuppressWarnings("unchecked")
                        Parameter<Constructor<?>> unchecked = (Parameter<Constructor<?>>) parameterInfo.get();
                        parameter = unchecked;
                    } catch (ClassNotFoundException e) {
                        continue;
                    }
                    IncludesClassfile<Parameter<Constructor<?>>> annotated =
                        new IncludesClassfile<Parameter<Constructor<?>>>(parameter,
                            classfileAnnotationsFor(parameterInfo));
                    if (annotated.isAnnotationPresent(annotation)) {
                        result.add(annotated);
                    }
                }
            }
            return result;
        }

        public List<Annotated<Field>> findAnnotatedFields(Class<? extends Annotation> annotation) {
            Finder.this.findAnnotatedFields(annotation);
            final List<Annotated<Field>> result = new ArrayList<Annotated<Field>>();
            for (Info info : getAnnotationInfos(annotation.getName())) {
                if (info instanceof FieldInfo) {
                    FieldInfo fieldInfo = (FieldInfo) info;
                    try {
                        IncludesClassfile<Field> annotated =
                            new IncludesClassfile<Field>((Field) fieldInfo.get(), classfileAnnotationsFor(fieldInfo));
                        if (annotated.isAnnotationPresent(annotation)) {
                            result.add(annotated);
                        }
                    } catch (ClassNotFoundException e) {
                        continue;
                    }
                }
            }
            return result;
        }

        private List<Annotation> classfileAnnotationsFor(Info info) {
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
            protected java.util.Map<Info, java.util.List<Annotation>> initialValue() {
                return new IdentityHashMap<AnnotationFinder.Info, List<Annotation>>();
            }
        };

    private final WithAnnotations withAnnotations = new WithAnnotations();
    private final Map<Info, List<Annotation>> classfileAnnotations;
    private final Inflater inflater;

    /**
     * Create a new {@link Finder} instance.
     * 
     * @param archive
     */
    public Finder(Archive archive) {
        super(archive, false);
        classfileAnnotations = CLASSFILE_ANNOTATIONS.get();
        CLASSFILE_ANNOTATIONS.remove();
        inflater = new Inflater(classfileAnnotations);
        enableFindImplementations();
        enableFindSubclasses();
    }

    public WithAnnotations withAnnotations() {
        return withAnnotations;
    }

    protected void readClassDef(InputStream in) throws IOException {
        try {
            ClassReader classReader = new ClassReader(in);
            classReader.accept(new Visitor(new InfoBuildingVisitor()), ASM_FLAGS);
        } finally {
            in.close();
        }
    }

    @Override
    public AnnotationFinder select(Class<?>... arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationFinder select(Iterable<String> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationFinder select(String... clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScanResult scan(ScanRequest request) {
        final ScanResult result = new ScanResult();

        for (WeaveInterest interest : request.getInterests()) {
            switch (interest.target) {
                case PACKAGE:
                    for (Annotated<Package> pkg : this.withAnnotations().findAnnotatedPackages(interest.annotationType)) {
                        result.getWeavable(pkg.get()).addAnnotations(pkg.getAnnotations());
                    }
                case TYPE:
                    for (Annotated<Class<?>> type : this.withAnnotations()
                        .findAnnotatedClasses(interest.annotationType)) {
                        result.getWeavable(type.get()).addAnnotations(type.getAnnotations());
                    }
                    break;
                case METHOD:
                    for (Annotated<Method> method : this.withAnnotations()
                        .findAnnotatedMethods(interest.annotationType)) {
                        result.getWeavable(method.get()).addAnnotations(method.getAnnotations());
                    }
                    break;
                case CONSTRUCTOR:
                    for (Annotated<Constructor<?>> cs : this.withAnnotations().findAnnotatedConstructors(
                        interest.annotationType)) {
                        result.getWeavable(cs.get()).addAnnotations(cs.getAnnotations());
                    }
                    break;
                case FIELD:
                    for (Annotated<Field> fld : this.withAnnotations().findAnnotatedFields(interest.annotationType)) {
                        result.getWeavable(fld.get()).addAnnotations(fld.getAnnotations());
                    }
                    break;
                case PARAMETER:
                    for (Annotated<Parameter<Method>> parameter : this.withAnnotations().findAnnotatedMethodParameters(
                        interest.annotationType)) {
                        result.getWeavable(parameter.get().getDeclaringExecutable())
                            .getWeavableParameter(parameter.get().getIndex())
                            .addAnnotations(parameter.getAnnotations());
                    }
                    for (Annotated<Parameter<Constructor<?>>> parameter : this.withAnnotations()
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
        for (Class<?> supertype : request.getSupertypes()) {
            for (Annotated<Class<?>> type : this.withAnnotations().findAssignableTypes(supertype)) {
                result.getWeavable(type.get()).addAnnotations(type.getAnnotations());
            }
        }
        return inflater.inflate(result);
    }

    private Class<?> toClass(Type type) {
        final String className = (type.getSort() == Type.ARRAY ? type.getElementType() : type).getClassName();
        Class<?> result;
        try {
            result = Class.forName(className);
        } catch (ClassNotFoundException e) {
            try {
                result = getArchive().loadClass(className);
            } catch (ClassNotFoundException e1) {
                throw new RuntimeException(e1);
            }
        }
        if (type.getSort() == Type.ARRAY) {
            int[] dims = new int[type.getDimensions()];
            Arrays.fill(dims, 0);
            result = Array.newInstance(result, dims).getClass();
        }
        return result;
    }
}
