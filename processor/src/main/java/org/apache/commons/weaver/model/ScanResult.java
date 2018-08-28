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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.WeaveProcessor;
import org.apache.commons.weaver.spi.Weaver;

/**
 * <p>Encapsulates the result of scanning based on a {@link ScanRequest}. The
 * scan results are available in a structure corresponding to the Java class
 * hierarchy; i.e.:
 * </p>
 * <pre>
 *   package
 *   |_class
 *     |_field
 *     |_method
 *     | |_method parameter
 *     |_constructor
 *       |_constructor parameter
 * </pre>
 * <p>
 * The tree of results can be iterated in this manner using
 * {@link #getPackages()}. However, if a given {@link Weaver} is known not to
 * handle packages but some other element, convenience methods are provided
 * here giving direct access to the various elements that may have been
 * discovered.
 * </p>
 */
public class ScanResult {
    private abstract static class Projection<PARENT, CHILD extends AnnotatedElement> implements
        AnnotatedElements<CHILD> {
        private final Iterable<PARENT> parents;

        Projection(final Iterable<PARENT> parents) {
            super();
            this.parents = parents;
        }

        protected abstract Iterable<CHILD> childrenOf(PARENT parent);

        @Override
        public Iterator<CHILD> iterator() {
            final Iterator<PARENT> parentIterator = parents.iterator();
            return new Iterator<CHILD>() {
                private Iterator<CHILD> children = nextChildren();

                @Override
                public synchronized boolean hasNext() {
                    return children != null;
                }

                @Override
                public synchronized CHILD next() {
                    if (children == null) {
                        throw new NoSuchElementException();
                    }
                    try {
                        return children.next();
                    } finally {
                        if (!children.hasNext()) {
                            children = nextChildren();
                        }
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

                private Iterator<CHILD> nextChildren() {
                    while (parentIterator.hasNext()) {
                        final Iterator<CHILD> prospect = childrenOf(parentIterator.next()).iterator();
                        if (prospect.hasNext()) {
                            return prospect;
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public AnnotatedElements<CHILD> with(final Class<? extends Annotation> annotationType) {
            return new AnnotatedWith<>(this, annotationType);
        }
    }

    private static class AnnotatedWith<W extends AnnotatedElement> implements AnnotatedElements<W> {
        final Iterable<W> wrapped;
        final Class<? extends Annotation> annotationType;

        AnnotatedWith(final Iterable<W> wrapped, final Class<? extends Annotation> annotationType) {
            super();
            this.wrapped = wrapped;
            this.annotationType = annotationType;
        }

        @Override
        public Iterator<W> iterator() {
            final Iterator<W> iter = wrapped.iterator();
            return new Iterator<W>() {
                W next = read();

                private W read() {
                    while (iter.hasNext()) {
                        final W element = iter.next();
                        if (element.isAnnotationPresent(annotationType)) {
                            return element;
                        }
                    }
                    return null;
                }

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public W next() {
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                    try {
                        return next;
                    } finally {
                        next = read();
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public AnnotatedElements<W> with(final Class<? extends Annotation> annotationType) {
            return new AnnotatedWith<>(this, annotationType);
        }

    }

    /**
     * Weavable packages by name.
     */
    final ConcurrentNavigableMap<String, WeavablePackage> packages = new ConcurrentSkipListMap<>();

    /**
     * Public for use by {@link WeaveProcessor}.
     * @param pkg to wrap
     * @return {@link WeavablePackage}
     */
    public WeavablePackage getWeavable(final Package pkg) {
        final String key =
            Optional.ofNullable(pkg).map(Package::getName).orElse("");

        if (packages.containsKey(key)) {
            return packages.get(key);
        }
        final WeavablePackage result = new WeavablePackage(pkg);
        final WeavablePackage faster = packages.putIfAbsent(key, result);
        return faster == null ? result : faster;
    }

    /**
     * Public for use by {@link WeaveProcessor}.
     * @param cls to wrap
     * @param <T> type
     * @return {@link WeavableClass}
     */
    public <T> WeavableClass<T> getWeavable(final Class<T> cls) {
        return getWeavable(cls.getPackage()).getWeavable(cls);
    }

    /**
     * Public for use by {@link WeaveProcessor}.
     * @param fld to wrap
     * @return {@link WeavableField}
     */
    public WeavableField<?> getWeavable(final Field fld) {
        return getWeavable(fld.getDeclaringClass()).getWeavable(fld);
    }

    /**
     * Public for use by {@link WeaveProcessor}.
     * @param methd to wrap
     * @return {@link WeavableMethod}
     */
    public WeavableMethod<?> getWeavable(final Method methd) {
        return getWeavable(methd.getDeclaringClass()).getWeavable(methd);
    }

    /**
     * Public for use by {@link WeaveProcessor}.
     * @param ctor to wrap
     * @param <T> type
     * @return {@link WeavableConstructor}
     */
    public <T> WeavableConstructor<T> getWeavable(final Constructor<T> ctor) {
        return getWeavable(ctor.getDeclaringClass()).getWeavable(ctor);
    }

    /**
     * Iterate or filter {@link WeavablePackage}s.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavablePackage> getPackages() {
        return new AnnotatedElements<WeavablePackage>() {

            @Override
            public Iterator<WeavablePackage> iterator() {
                return packages.values().iterator();
            }

            @Override
            public AnnotatedElements<WeavablePackage> with(final Class<? extends Annotation> annotationType) {
                return new AnnotatedWith<>(packages.values(), annotationType);
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableClass}es.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableClass<?>> getClasses() {
        return new Projection<WeavablePackage, WeavableClass<?>>(getPackages()) {

            @Override
            protected Iterable<WeavableClass<?>> childrenOf(final WeavablePackage parent) {
                return parent.getClasses();
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableClass}es assignable to {@code supertype}.
     * @param supertype {@link Class} whose subtypes are sought
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableClass<?>> getClassesAssignableTo(final Class<?> supertype) {
        Validate.notNull(supertype, "supertype");

        return new Projection<WeavablePackage, WeavableClass<?>>(getPackages()) {

            @Override
            protected Iterable<WeavableClass<?>> childrenOf(final WeavablePackage parent) {
                return parent.getClasses();
            }

            @Override
            public Iterator<WeavableClass<?>> iterator() {
                final Iterator<WeavableClass<?>> toWrap = super.iterator();
                return new Iterator<WeavableClass<?>>() {
                    {
                        read();
                    }

                    private WeavableClass<?> next;

                    private void read() {
                        while (toWrap.hasNext()) {
                            final WeavableClass<?> test = toWrap.next();
                            if (supertype.isAssignableFrom(test.getTarget())) {
                                next = test;
                                return;
                            }
                        }
                        next = null;
                    }

                    @Override
                    public boolean hasNext() {
                        return next != null;
                    }

                    @Override
                    public WeavableClass<?> next() {
                        try {
                            return next;
                        } finally {
                            read();
                        }
                    }

                    @Override
                    public void remove() {
                        toWrap.remove();
                    }
                };
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableField}s.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableField<?>> getFields() {
        return new Projection<WeavableClass<?>, WeavableField<?>>(getClasses()) {

            @Override
            protected Iterable<WeavableField<?>> childrenOf(final WeavableClass<?> parent) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final Iterable<WeavableField<?>> result = ((WeavableClass) parent).getFields();
                return result;
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableConstructor}s.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableConstructor<?>> getConstructors() {
        return new Projection<WeavableClass<?>, WeavableConstructor<?>>(getClasses()) {

            @Override
            protected Iterable<WeavableConstructor<?>> childrenOf(final WeavableClass<?> parent) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final Iterable<WeavableConstructor<?>> result = ((WeavableClass) parent).getConstructors();
                return result;
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableMethod}s.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableMethod<?>> getMethods() {
        return new Projection<WeavableClass<?>, WeavableMethod<?>>(getClasses()) {

            @Override
            protected Iterable<WeavableMethod<?>> childrenOf(final WeavableClass<?> parent) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final Iterable<WeavableMethod<?>> result = ((WeavableClass) parent).getMethods();
                return result;
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableMethodParameter}s.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableMethodParameter<?>> getMethodParameters() {
        return new Projection<WeavableMethod<?>, WeavableMethodParameter<?>>(getMethods()) {

            @Override
            protected Iterable<WeavableMethodParameter<?>> childrenOf(final WeavableMethod<?> parent) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final Iterable<WeavableMethodParameter<?>> result = ((WeavableMethod) parent).getParameters();
                return result;
            }
        };
    }

    /**
     * Iterate or filter {@link WeavableConstructorParameter}s.
     * @return {@link AnnotatedElements}
     */
    public AnnotatedElements<WeavableConstructorParameter<?>> getConstructorParameters() {

        return new Projection<WeavableConstructor<?>, WeavableConstructorParameter<?>>(getConstructors()) {

            @Override
            protected Iterable<WeavableConstructorParameter<?>> childrenOf(final WeavableConstructor<?> parent) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final Iterable<WeavableConstructorParameter<?>> result = ((WeavableConstructor) parent).getParameters();
                return result;
            }
        };
    }
}
