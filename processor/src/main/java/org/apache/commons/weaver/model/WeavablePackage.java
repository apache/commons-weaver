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

import java.util.Collections;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class WeavablePackage extends Weavable<WeavablePackage, Package> {

    private final ConcurrentNavigableMap<String, WeavableClass<?>> clazzes =
        new ConcurrentSkipListMap<String, WeavableClass<?>>();

    public WeavablePackage(Package target) {
        super(target);
    }

    public synchronized <T> WeavableClass<T> getWeavable(Class<T> cls) {
        final String key = cls.getName();
        if (clazzes.containsKey(key)) {
            @SuppressWarnings("unchecked")
            final WeavableClass<T> result = (WeavableClass<T>) clazzes.get(key);
            return result;
        }
        final WeavableClass<T> result = new WeavableClass<T>(cls, this);
        @SuppressWarnings("unchecked")
        final WeavableClass<T> faster = (WeavableClass<T>) clazzes.putIfAbsent(key, result);
        return faster == null ? result : faster;
    }

    public Iterable<WeavableClass<?>> getClasses() {
        return Collections.unmodifiableCollection(clazzes.values());
    }

    @Override
    public int compareTo(WeavablePackage arg0) {
        return getTarget().getName().compareTo(arg0.getTarget().getName());
    }
}
