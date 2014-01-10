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
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;

/**
 * {@link Weavable} extends {@link AnnotatedElement} to include
 * {@link RetentionPolicy#CLASS} annotations.
 *
 * @param <SELF> own type
 * @param <TARGET> target type
 */
public abstract class Weavable<SELF extends Weavable<SELF, TARGET>, TARGET> implements Comparable<SELF>,
    AnnotatedElement {
    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    private final TARGET target;
    private Set<Annotation> annotations;

    /**
     * Create a new {@link Weavable} instance.
     * @param target {@code TARGET}
     */
    protected Weavable(TARGET target) {
        this.target = target;
        if (target instanceof AnnotatedElement) {
            addAnnotations(((AnnotatedElement) target).getAnnotations());
        }
    }

    /**
     * Add one or more annotations.
     * @param toAdd {@link Annotation}[]
     * @return whether any change was made
     */
    public boolean addAnnotations(Annotation... toAdd) {
        Validate.noNullElements(toAdd);
        return addAnnotations(Arrays.asList(toAdd));
    }

    /**
     * Add annotations from an {@link Iterable}.
     * @param toAdd {@link Iterable} of {@link Annotation}
     * @return whether any change was made
     */
    public boolean addAnnotations(Iterable<Annotation> toAdd) {
        if (toAdd == null) {
            return false;
        }
        synchronized (this) {
            if (annotations == null) {
                annotations = new LinkedHashSet<Annotation>();
            }
        }
        boolean result = false;
        for (Annotation a : toAdd) {
            result = a != null && annotations.add(a) || result;
        }
        return result;
    }

    /**
     * Get the target of this {@link Weavable}.
     * @return {@code TARGET}
     */
    public TARGET getTarget() {
        return target;
    }

    /**
     * Get all {@link Annotation}s associated with this element.
     * @return {@link Annotation}[]
     */
    @Override
    public final Annotation[] getAnnotations() {
        synchronized (this) {
            if (annotations == null) {
                return EMPTY_ANNOTATION_ARRAY;
            }
        }
        return annotations.toArray(EMPTY_ANNOTATION_ARRAY);
    }

    /**
     * Get any instance of {@code annotationClass} attached to {@link #getTarget()}.
     * @param annotationClass {@link Class} annotation type
     * @param <T> annotation type
     * @return {@code T} instance if available, else {@code null}
     */
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

    /**
     * Overridden to return {@link #getAnnotations()}.
     * @return {@link Annotation}[]
     */
    @Override
    public final Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }

    /**
     * Learn whether an annotation of type {@code annotationClass} is present.
     * @param annotationClass to find
     * @return {@code boolean}
     */
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    /**
     * Return a {@link String} representation of this {@link Weavable}.
     * @return {@link String}
     */
    @Override
    public String toString() {
        return "Weavable " + getTarget().toString();
    }
}
