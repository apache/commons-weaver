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

        InfoMatcher(Class<? extends Info> type) {
            super();
            this.type = type;
        }

        boolean test(Info info) {
            return type.isInstance(info);
        }

    }

    private class MethodMatcher extends InfoMatcher {
        final boolean cs;

        MethodMatcher(boolean cs) {
            super(MethodInfo.class);
            this.cs = cs;
        }

        @Override
        boolean test(Info info) {
            return super.test(info) && ((MethodInfo) info).isConstructor() == cs;
        }
    }

    private class ParameterMatcher extends InfoMatcher {
        final boolean cs;

        ParameterMatcher(boolean cs) {
            super(ParameterInfo.class);
            this.cs = cs;
        }

        @Override
        boolean test(Info info) {
            return super.test(info) && ((ParameterInfo) info).getDeclaringMethod().isConstructor() == cs;
        }
    }

    final Map<PackageInfo, List<Annotation>> packageAnnotations;
    final Map<ClassInfo, List<Annotation>> classAnnotations;
    final Map<FieldInfo, List<Annotation>> fieldAnnotations;
    final Map<MethodInfo, List<Annotation>> constructorAnnotations;
    final Map<MethodInfo, List<Annotation>> methodAnnotations;
    final Map<ParameterInfo, List<Annotation>> constructorParameterAnnotations;
    final Map<ParameterInfo, List<Annotation>> methodParameterAnnotations;

    Inflater(Map<Info, List<Annotation>> m) {
        super();

        this.packageAnnotations = subMap(m, new InfoMatcher(PackageInfo.class));
        this.classAnnotations = subMap(m, new InfoMatcher(ClassInfo.class));
        this.fieldAnnotations = subMap(m, new InfoMatcher(FieldInfo.class));
        this.constructorAnnotations = subMap(m, new MethodMatcher(true));
        this.methodAnnotations = subMap(m, new MethodMatcher(false));
        this.constructorParameterAnnotations = subMap(m, new ParameterMatcher(true));
        this.methodParameterAnnotations = subMap(m, new ParameterMatcher(false));
    }

    static <I extends Info> Map<I, List<Annotation>> subMap(Map<Info, List<Annotation>> source, InfoMatcher matcher) {
        final HashMap<I, List<Annotation>> result = new HashMap<I, List<Annotation>>();
        for (Map.Entry<Info, List<Annotation>> e : source.entrySet()) {
            if (matcher.test(e.getKey())) {
                @SuppressWarnings("unchecked")
                final I key = (I) e.getKey();
                result.put(key, e.getValue());
            }
        }
        return result;
    }

    ScanResult inflate(final ScanResult scanResult) {
        for (WeavablePackage pkg : scanResult.getPackages()) {
            for (Map.Entry<PackageInfo, List<Annotation>> e : packageAnnotations.entrySet()) {
                if (e.getKey().getName().equals(pkg.getTarget().getName())) {
                    pkg.addAnnotations(e.getValue());
                }
            }
            for (WeavableClass<?> cls : pkg.getClasses()) {
                for (Map.Entry<ClassInfo, List<Annotation>> e : classAnnotations.entrySet()) {
                    if (e.getKey().getName().equals(cls.getTarget().getName())) {
                        cls.addAnnotations(e.getValue());
                    }
                }
                for (WeavableField<?> fld : cls.getFields()) {
                    for (Map.Entry<FieldInfo, List<Annotation>> e : fieldAnnotations.entrySet()) {
                        try {
                            if (e.getKey().get().equals(fld.getTarget())) {
                                fld.addAnnotations(e.getValue());
                            }
                        } catch (ClassNotFoundException cnfe) {
                            // :/
                        }
                    }
                }
                for (WeavableConstructor<?> cs : cls.getConstructors()) {
                    for (Map.Entry<MethodInfo, List<Annotation>> e : constructorAnnotations.entrySet()) {
                        try {
                            if (e.getKey().get().equals(cs.getTarget())) {
                                cs.addAnnotations(e.getValue());
                            }
                        } catch (ClassNotFoundException cnfe) {
                            // :/
                        }
                    }
                    for (WeavableConstructorParameter<?> p : cs.getParameters()) {
                        for (Map.Entry<ParameterInfo, List<Annotation>> e : constructorParameterAnnotations.entrySet()) {
                            try {
                                final Parameter<?> parameter = e.getKey().get();
                                if (parameter.getDeclaringExecutable().equals(cs.getTarget())
                                    && p.getTarget().intValue() == parameter.getIndex()) {
                                    p.addAnnotations(e.getValue());
                                }
                            } catch (ClassNotFoundException cnfe) {
                                // :/
                            }
                        }
                    }
                }
                for (WeavableMethod<?> mt : cls.getMethods()) {
                    for (Map.Entry<MethodInfo, List<Annotation>> e : methodAnnotations.entrySet()) {
                        try {
                            if (e.getKey().get().equals(mt.getTarget())) {
                                mt.addAnnotations(e.getValue());
                            }
                        } catch (ClassNotFoundException cnfe) {
                            // :/
                        }
                    }
                    for (WeavableMethodParameter<?> p : mt.getParameters()) {
                        for (Map.Entry<ParameterInfo, List<Annotation>> e : methodParameterAnnotations.entrySet()) {
                            try {
                                final Parameter<?> parameter = e.getKey().get();
                                if (parameter.getDeclaringExecutable().equals(mt.getTarget())
                                    && p.getTarget().intValue() == parameter.getIndex()) {
                                    p.addAnnotations(e.getValue());
                                }
                            } catch (ClassNotFoundException cnfe) {
                                // :/
                            }
                        }
                    }
                }
            }
        }
        return scanResult;
    }

}
