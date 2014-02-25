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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private class InfoMatcher {
        final Class<? extends Info> type;

        InfoMatcher(final Class<? extends Info> type) {
            super();
            this.type = type;
        }

        boolean test(final Info info) {
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
        boolean test(final Info info) {
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
        boolean test(final Info info) {
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
        final HashMap<I, List<Annotation>> result = new HashMap<I, List<Annotation>>();
        for (final Map.Entry<Info, List<Annotation>> entry : source.entrySet()) {
            if (matcher.test(entry.getKey())) {
                @SuppressWarnings("unchecked")
                final I key = (I) entry.getKey();
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    ScanResult inflate(final ScanResult scanResult) {
        for (final WeavablePackage pkg : scanResult.getPackages()) {
            for (final Map.Entry<PackageInfo, List<Annotation>> entry : packageAnnotations.entrySet()) {
                if (entry.getKey().getName().equals(pkg.getTarget().getName())) {
                    pkg.addAnnotations(entry.getValue());
                }
            }
            for (final WeavableClass<?> cls : pkg.getClasses()) {
                for (final Map.Entry<ClassInfo, List<Annotation>> entry : classAnnotations.entrySet()) {
                    if (entry.getKey().getName().equals(cls.getTarget().getName())) {
                        cls.addAnnotations(entry.getValue());
                    }
                }
                for (final WeavableField<?> fld : cls.getFields()) {
                    for (final Map.Entry<FieldInfo, List<Annotation>> entry : fieldAnnotations.entrySet()) {
                        try {
                            if (entry.getKey().get().equals(fld.getTarget())) {
                                fld.addAnnotations(entry.getValue());
                            }
                        } catch (final ClassNotFoundException cnfe) {
                            continue;
                        }
                    }
                }
                for (final WeavableConstructor<?> ctor : cls.getConstructors()) {
                    for (final Map.Entry<MethodInfo, List<Annotation>> entry : ctorAnnotations.entrySet()) {
                        try {
                            if (entry.getKey().get().equals(ctor.getTarget())) {
                                ctor.addAnnotations(entry.getValue());
                            }
                        } catch (final ClassNotFoundException cnfe) {
                            continue;
                        }
                    }
                    for (final WeavableConstructorParameter<?> param : ctor.getParameters()) {
                        for (final Map.Entry<ParameterInfo, List<Annotation>> entry : ctorParameterAnnotations
                            .entrySet()) {
                            try {
                                final Parameter<?> parameter = entry.getKey().get();
                                if (parameter.getDeclaringExecutable().equals(ctor.getTarget())
                                    && param.getTarget().intValue() == parameter.getIndex()) {
                                    param.addAnnotations(entry.getValue());
                                }
                            } catch (final ClassNotFoundException cnfe) {
                                continue;
                            }
                        }
                    }
                }
                for (final WeavableMethod<?> methd : cls.getMethods()) {
                    for (final Map.Entry<MethodInfo, List<Annotation>> entry : methodAnnotations.entrySet()) {
                        try {
                            if (entry.getKey().get().equals(methd.getTarget())) {
                                methd.addAnnotations(entry.getValue());
                            }
                        } catch (final ClassNotFoundException cnfe) {
                            continue;
                        }
                    }
                    for (final WeavableMethodParameter<?> param : methd.getParameters()) {
                        for (final Map.Entry<ParameterInfo, List<Annotation>> entry : methodParameterAnnotations
                            .entrySet()) {
                            try {
                                final Parameter<?> parameter = entry.getKey().get();
                                if (parameter.getDeclaringExecutable().equals(methd.getTarget())
                                    && param.getTarget().intValue() == parameter.getIndex()) {
                                    param.addAnnotations(entry.getValue());
                                }
                            } catch (final ClassNotFoundException cnfe) {
                                continue;
                            }
                        }
                    }
                }
            }
        }
        return scanResult;
    }

}
