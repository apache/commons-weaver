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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeavableConstructor;
import org.apache.commons.weaver.model.WeavableConstructorParameter;
import org.apache.commons.weaver.model.WeavableField;
import org.apache.commons.weaver.model.WeavableMethod;
import org.apache.commons.weaver.model.WeavableMethodParameter;
import org.apache.commons.weaver.model.WeavablePackage;
import org.apache.xbean.finder.AnnotationFinder.ClassInfo;
import org.apache.xbean.finder.AnnotationFinder.FieldInfo;
import org.apache.xbean.finder.AnnotationFinder.Info;
import org.apache.xbean.finder.AnnotationFinder.MethodInfo;
import org.apache.xbean.finder.AnnotationFinder.PackageInfo;
import org.apache.xbean.finder.AnnotationFinder.ParameterInfo;
import org.apache.xbean.finder.Parameter;

/**
 * Adds all classfile annotations to a ScanResult.
 */
class Inflater {
    private class InfoMatcher implements Predicate<Info> {
        final Class<? extends Info> type;

        InfoMatcher(final Class<? extends Info> type) {
            super();
            this.type = type;
        }

        public boolean test(final Info info) {
            return type.isInstance(info);
        }
    }

    private class MethodMatcher extends InfoMatcher {
        final boolean isCtor;

        MethodMatcher(final boolean isCtor) {
            super(MethodInfo.class);
            this.isCtor = isCtor;
        }

        @Override
        public boolean test(final Info info) {
            return super.test(info) && ((MethodInfo) info).isConstructor() == isCtor;
        }
    }

    private class ParameterMatcher extends InfoMatcher {
        final boolean isCtor;

        ParameterMatcher(final boolean isCtor) {
            super(ParameterInfo.class);
            this.isCtor = isCtor;
        }

        @Override
        public boolean test(final Info info) {
            return super.test(info) && ((ParameterInfo) info).getDeclaringMethod().isConstructor() == isCtor;
        }
    }

    final Map<PackageInfo, List<Annotation>> packageAnnotations;
    final Map<ClassInfo, List<Annotation>> classAnnotations;
    final Map<FieldInfo, List<Annotation>> fieldAnnotations;
    final Map<MethodInfo, List<Annotation>> ctorAnnotations;
    final Map<MethodInfo, List<Annotation>> methodAnnotations;
    final Map<ParameterInfo, List<Annotation>> ctorParameterAnnotations;
    final Map<ParameterInfo, List<Annotation>> methodParameterAnnotations;

    Inflater(final Map<Info, List<Annotation>> annotationMap) {
        super();

        this.packageAnnotations = subMap(annotationMap, new InfoMatcher(PackageInfo.class));
        this.classAnnotations = subMap(annotationMap, new InfoMatcher(ClassInfo.class));
        this.fieldAnnotations = subMap(annotationMap, new InfoMatcher(FieldInfo.class));
        this.ctorAnnotations = subMap(annotationMap, new MethodMatcher(true));
        this.methodAnnotations = subMap(annotationMap, new MethodMatcher(false));
        this.ctorParameterAnnotations = subMap(annotationMap, new ParameterMatcher(true));
        this.methodParameterAnnotations = subMap(annotationMap, new ParameterMatcher(false));
    }

    static <I extends Info> Map<I, List<Annotation>> subMap(final Map<Info, List<Annotation>> source,
        final InfoMatcher matcher) {
        @SuppressWarnings("unchecked")
        final Map<I, List<Annotation>> result = source.entrySet().stream().filter(e -> matcher.test(e.getKey()))
            .collect(Collectors.toMap(t -> (I) t.getKey(), Map.Entry::getValue));

        return result;
    }

    ScanResult inflate(final ScanResult scanResult) {
        for (final WeavablePackage pkg : scanResult.getPackages()) {
            packageAnnotations.forEach((k, v) -> {
                if (k.getName().equals(pkg.getTarget().getName())) {
                    pkg.addAnnotations(v);
                }
            });
            for (final WeavableClass<?> cls : pkg.getClasses()) {
                classAnnotations.forEach((k, v) -> {
                    if (k.getName().equals(cls.getTarget().getName())) {
                        cls.addAnnotations(v);
                    }
                });
                for (final WeavableField<?> fld : cls.getFields()) {
                    fieldAnnotations.forEach((k, v) -> {
                        try {
                            if (k.get().equals(fld.getTarget())) {
                                fld.addAnnotations(v);
                            }
                        } catch (final ClassNotFoundException ignored) {
                        }
                    });
                }
                for (final WeavableConstructor<?> ctor : cls.getConstructors()) {
                    ctorAnnotations.forEach((k, v) -> {
                        try {
                            if (k.get().equals(ctor.getTarget())) {
                                ctor.addAnnotations(v);
                            }
                        } catch (final ClassNotFoundException ignored) {
                        }
                    });
                    for (final WeavableConstructorParameter<?> param : ctor.getParameters()) {
                        ctorParameterAnnotations.forEach((k, v) -> {
                            try {
                                final Parameter<?> parameter = k.get();
                                if (parameter.getDeclaringExecutable().equals(ctor.getTarget())
                                    && param.getTarget().intValue() == parameter.getIndex()) {
                                    param.addAnnotations(v);
                                }
                            } catch (final ClassNotFoundException ignored) {
                            }
                        });
                    }
                }
                for (final WeavableMethod<?> methd : cls.getMethods()) {
                    methodAnnotations.forEach((k, v) -> {
                        try {
                            if (k.get().equals(methd.getTarget())) {
                                methd.addAnnotations(v);
                            }
                        } catch (final ClassNotFoundException ignored) {
                        }
                    });
                    for (final WeavableMethodParameter<?> param : methd.getParameters()) {
                        methodParameterAnnotations.forEach((k, v) -> {
                            try {
                                final Parameter<?> parameter = k.get();
                                if (parameter.getDeclaringExecutable().equals(methd.getTarget())
                                    && param.getTarget().intValue() == parameter.getIndex()) {
                                    param.addAnnotations(v);
                                }
                            } catch (final ClassNotFoundException ignored) {
                            }
                        });
                    }
                }
            }
        }
        return scanResult;
    }
}
