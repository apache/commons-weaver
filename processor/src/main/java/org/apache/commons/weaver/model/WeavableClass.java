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
import java.util.Comparator;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.weaver.utils.Args;

public class WeavableClass<T> extends NestedWeavable<WeavableClass<T>, Class<T>, WeavablePackage, Package> {
    private final ConcurrentNavigableMap<String, WeavableField<T>> fields =
        new ConcurrentSkipListMap<String, WeavableField<T>>();
    private final ConcurrentNavigableMap<Constructor<T>, WeavableConstructor<T>> ctors =
        new ConcurrentSkipListMap<Constructor<T>, WeavableConstructor<T>>(new Comparator<Constructor<?>>() {

            @Override
            public int compare(Constructor<?> o1, Constructor<?> o2) {
                return Args.compare(o1.getParameterTypes(), o2.getParameterTypes());
            }
        });
    private final ConcurrentNavigableMap<Method, WeavableMethod<T>> methods =
        new ConcurrentSkipListMap<Method, WeavableMethod<T>>(new Comparator<Method>() {

            @Override
            public int compare(Method o1, Method o2) {
                int result = o1.getName().compareTo(o2.getName());
                return result == 0 ? Args.compare(o1.getParameterTypes(), o2.getParameterTypes()) : result;
            }
        });

    public WeavableClass(Class<T> target, WeavablePackage parent) {
        super(target, parent);
    }

    public WeavableField<T> getWeavable(Field fld) {
        final String key = fld.getName();
        if (fields.containsKey(key)) {
            final WeavableField<T> result = (WeavableField<T>) fields.get(key);
            return result;
        }
        final WeavableField<T> result = new WeavableField<T>(fld, this);
        final WeavableField<T> faster = (WeavableField<T>) fields.putIfAbsent(key, result);
        return faster == null ? result : faster;
    }

    public WeavableMethod<T> getWeavable(Method mt) {
        if (methods.containsKey(mt)) {
            final WeavableMethod<T> result = (WeavableMethod<T>) methods.get(mt);
            return result;
        }
        final WeavableMethod<T> result = (WeavableMethod<T>) new WeavableMethod<T>(mt, this);
        final WeavableMethod<T> faster = (WeavableMethod<T>) methods.putIfAbsent(mt, result);
        return faster == null ? result : faster;
    }

    public WeavableConstructor<T> getWeavable(Constructor<T> ctor) {
        if (ctors.containsKey(ctor)) {
            final WeavableConstructor<T> result = (WeavableConstructor<T>) ctors.get(ctor);
            return result;
        }
        final WeavableConstructor<T> result = (WeavableConstructor<T>) new WeavableConstructor<T>(ctor, this);
        final WeavableConstructor<T> faster = (WeavableConstructor<T>) ctors.putIfAbsent(ctor, result);
        return faster == null ? result : faster;
    }

    public Iterable<WeavableField<T>> getFields() {
        return Collections.unmodifiableCollection(fields.values());
    }

    public Iterable<WeavableConstructor<T>> getConstructors() {
        return Collections.unmodifiableCollection(ctors.values());
    }

    public Iterable<WeavableMethod<T>> getMethods() {
        return Collections.unmodifiableCollection(methods.values());
    }

    @Override
    protected int localCompareTo(WeavableClass<T> o) {
        return getTarget().getName().compareTo(o.getTarget().getName());
    }
}
