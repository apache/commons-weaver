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
package org.apache.commons.weaver.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.AnnotationUtils;
import org.apache.commons.lang3.Validate;

/**
 * Provide annotation-related utility methods.
 */
public final class Annotations {
    private Annotations() {
    }

    /**
     * Create an annotation instance.
     * @param annotationType type
     * @param elements values
     * @param <A> generic annotation type
     * @return {@code A}
     */
    public static <A extends Annotation> A instanceOf(final Class<A> annotationType, final Map<String, ?> elements) {
        final ClassLoader proxyClassLoader = Validate.notNull(annotationType, "annotationType").getClassLoader();
        final InvocationHandler invocationHandler = new InvocationHandler() {

            @Override
            @SuppressWarnings("PMD.UseVarargs") // overridden method
            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                if (method.getDeclaringClass().equals(annotationType)) {
                    if (elements.containsKey(method.getName())) {
                        return elements.get(method.getName());
                    }
                    return method.getDefaultValue();
                }
                if ("annotationType".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return annotationType;
                }
                if ("equals".equals(method.getName())
                    && Arrays.equals(method.getParameterTypes(), new Class[] { Object.class })) {
                    return AnnotationUtils.equals((Annotation) proxy, (Annotation) args[0]);
                }
                if ("hashCode".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return AnnotationUtils.hashCode((Annotation) proxy);
                }
                if ("toString".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return AnnotationUtils.toString((Annotation) proxy);
                }
                throw new UnsupportedOperationException();
            }
        };
        @SuppressWarnings("unchecked")
        final A result =
            (A) Proxy.newProxyInstance(proxyClassLoader, new Class[] { annotationType }, invocationHandler);
        return result;
    }
}
