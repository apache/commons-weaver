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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.weaver.utils.Args;

/**
 * {@link Weavable} {@link Class}.
 *
 * @param <T> type
 */
public class WeavableClass<T> extends NestedWeavable<WeavableClass<T>, Class<T>, WeavablePackage, Package> {
    private final ConcurrentNavigableMap<String, WeavableField<T>> fields = new ConcurrentSkipListMap<>();

    private final ConcurrentNavigableMap<Constructor<T>, WeavableConstructor<T>> ctors = new ConcurrentSkipListMap<>(
        (ctor1, ctor2) -> Args.compare(ctor1.getParameterTypes(), ctor2.getParameterTypes()));

    private final ConcurrentNavigableMap<Method, WeavableMethod<T>> methods =
        new ConcurrentSkipListMap<>((methd1, methd2) -> {
            final int result = methd1.getName().compareTo(methd2.getName());
            return result == 0 ? Args.compare(methd1.getParameterTypes(), methd2.getParameterTypes()) : result;
        });

    /**
     * Create a new {@link WeavableClass} instance.
     * @param target {@link Class}
     * @param parent {@link WeavablePackage} enclosing
     */
    public WeavableClass(final Class<T> target, final WeavablePackage parent) {
        super(target, parent);
    }

    /**
     * Get a {@link WeavableField} representing {@code fld}.
     * @param fld to wrap
     * @return {@link WeavableField}
     */
    public WeavableField<T> getWeavable(final Field fld) {
        final String key = fld.getName();
        if (fields.containsKey(key)) {
            return fields.get(key);
        }
        final WeavableField<T> result = new WeavableField<>(fld, this);
        final WeavableField<T> faster = fields.putIfAbsent(key, result);
        return faster == null ? result : faster;
    }

    /**
     * Get a {@link WeavableMethod} representing {@code mt}.
     * @param methd to wrap
     * @return {@link WeavableMethod}
     */
    public WeavableMethod<T> getWeavable(final Method methd) {
        if (methods.containsKey(methd)) {
            return methods.get(methd);
        }
        final WeavableMethod<T> result = new WeavableMethod<>(methd, this);
        final WeavableMethod<T> faster = methods.putIfAbsent(methd, result);
        return faster == null ? result : faster;
    }

    /**
     * Get a {@link WeavableConstructor} representing {@code ctor}.
     * @param ctor to wrap
     * @return {@link WeavableConstructor}
     */
    public WeavableConstructor<T> getWeavable(final Constructor<T> ctor) {
        if (ctors.containsKey(ctor)) {
            return ctors.get(ctor);
        }
        final WeavableConstructor<T> result = new WeavableConstructor<>(ctor, this);
        final WeavableConstructor<T> faster = ctors.putIfAbsent(ctor, result);
        return faster == null ? result : faster;
    }

    /**
     * Get {@link WeavableField}s of this {@link WeavableClass}.
     * @return {@link Iterable}
     */
    public Iterable<WeavableField<T>> getFields() {
        return Collections.unmodifiableCollection(fields.values());
    }

    /**
     * Get {@link WeavableConstructor}s of this {@link WeavableClass}.
     * @return {@link Iterable}
     */
    public Iterable<WeavableConstructor<T>> getConstructors() {
        return Collections.unmodifiableCollection(ctors.values());
    }

    /**
     * Get {@link WeavableMethod}s of this {@link WeavableClass}.
     * @return {@link Iterable}
     */
    public Iterable<WeavableMethod<T>> getMethods() {
        return Collections.unmodifiableCollection(methods.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int localCompareTo(final WeavableClass<T> obj) {
        return getTarget().getName().compareTo(obj.getTarget().getName());
    }
}
