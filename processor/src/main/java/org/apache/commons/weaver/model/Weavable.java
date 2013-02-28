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
package org.apache.commons.weaver.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class Weavable<SELF extends Weavable<SELF, TARGET>, TARGET> implements Comparable<SELF>,
    AnnotatedElement {
    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    private final TARGET target;
    private Set<Annotation> annotations;

    protected Weavable(TARGET target) {
        this.target = target;
    }

    public boolean addAnnotations(Annotation... toAdd) {
        synchronized (this) {
            if (annotations == null) {
                annotations = new LinkedHashSet<Annotation>();
            }
        }
        return Collections.addAll(annotations, toAdd);
    }

    public TARGET getTarget() {
        return target;
    }

    public Annotation[] getAnnotations() {
        synchronized (this) {
            if (annotations == null) {
                return EMPTY_ANNOTATION_ARRAY;
            }
        }
        return annotations.toArray(EMPTY_ANNOTATION_ARRAY);
    }

    @Override
    public synchronized <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotations == null) {
            return null;
        }
        for (Annotation prospect : annotations) {
            if (annotationClass.equals(prospect.annotationType())) {
                @SuppressWarnings("unchecked")
                final T result = (T) prospect;
                return result;
            }
        }
        return null;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    @Override
    public String toString() {
        return "Weavable " + getTarget().toString();
    }
}
