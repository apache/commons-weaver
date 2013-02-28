package org.apache.commons.weaver;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.utils.Annotations;
import org.apache.xbean.asm.AnnotationVisitor;
import org.apache.xbean.asm.Attribute;
import org.apache.xbean.asm.ClassReader;
import org.apache.xbean.asm.FieldVisitor;
import org.apache.xbean.asm.MethodVisitor;
import org.apache.xbean.asm.Type;
import org.apache.xbean.asm.commons.EmptyVisitor;
import org.apache.xbean.finder.Annotated;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.Parameter;
import org.apache.xbean.finder.archive.Archive;

class Finder extends AnnotationFinder {

    private abstract class AnnotationInflater extends AnnotationCapturer {
        final Class<? extends Annotation> annotationType;
        final Map<String, Object> elements = new LinkedHashMap<String, Object>();

        AnnotationInflater(String desc) {
            super();
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

    private abstract class AnnotationCapturer implements AnnotationVisitor {

        protected abstract void storeValue(String name, Object value);

        @Override
        public void visit(String name, Object value) {
            storeValue(name, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String name, final String desc) {
            final AnnotationCapturer owner = this;
            return new AnnotationInflater(desc) {

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
            return new AnnotationCapturer() {

                @Override
                public void visitEnd() {
                    owner.storeValue(name, values.toArray());
                }

                @Override
                protected void storeValue(String name, Object value) {
                    values.add(value);
                }
            };
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
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

    public class Visitor extends EmptyVisitor {
        private final InfoBuildingVisitor wrapped;

        public Visitor(InfoBuildingVisitor wrapped) {
            super();
            this.wrapped = wrapped;
        }

        @Override
        public void visit(int arg0, int arg1, String arg2, String arg3, String arg4, String[] arg5) {
            wrapped.visit(arg0, arg1, arg2, arg3, arg4, arg5);
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            wrapped.visitAttribute(attribute);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            FieldVisitor toWrap = wrapped.visitField(access, name, desc, signature, value);
            return new Visitor((InfoBuildingVisitor) toWrap);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            wrapped.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor toWrap = wrapped.visitMethod(access, name, desc, signature, exceptions);
            return new Visitor((InfoBuildingVisitor) toWrap);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            wrapped.visitAnnotation(desc, visible);
            if (visible) {
                return null;
            }
            final Info info = wrapped.getInfo();

            return new AnnotationInflater(desc) {

                @Override
                public void visitEnd() {
                    classfileAnnotationsFor(info).add(inflate());
                }
            };
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int param, String desc, boolean visible) {
            wrapped.visitParameterAnnotation(param, desc, visible);
            if (visible) {
                return null;
            }
            final Info info = wrapped.getInfo();

            return new AnnotationInflater(desc) {

                @Override
                public void visitEnd() {
                    classfileAnnotationsFor(info).add(inflate());
                }
            };
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

    private static final ThreadLocal<Map<Info, List<Annotation>>> CLASSFILE_ANNOTATIONS =
        new ThreadLocal<Map<Info, List<Annotation>>>() {
            protected java.util.Map<Info, java.util.List<Annotation>> initialValue() {
                return new IdentityHashMap<AnnotationFinder.Info, List<Annotation>>();
            }
        };

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

    private final Map<Info, List<Annotation>> classfileAnnotations;

    public Finder(Archive archive) {
        super(archive, false);
        classfileAnnotations = CLASSFILE_ANNOTATIONS.get();
        CLASSFILE_ANNOTATIONS.remove();
    }

    public WithAnnotations withAnnotations() {
        return new WithAnnotations();
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
}
