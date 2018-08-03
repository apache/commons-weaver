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
import java.util.Comparator;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * {@link Weavable} {@link Package}.
 */
public class WeavablePackage extends Weavable<WeavablePackage, Package> {
    private static final Comparator<WeavablePackage> CMP = Comparator.nullsFirst(Comparator
        .comparing(WeavablePackage::getTarget, Comparator.nullsFirst(Comparator.comparing(Package::getName))));

    private final ConcurrentNavigableMap<String, WeavableClass<?>> clazzes = new ConcurrentSkipListMap<>();

    /**
     * Create a new {@link WeavablePackage} instance.
     * @param target package
     */
    public WeavablePackage(final Package target) {
        super(target);
    }

    /**
     * Get a {@link WeavableClass} representing {@code cls}.
     * @param cls to wrap
     * @param <T> generic type of {@code cls}
     * @return {@link WeavableClass}
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> WeavableClass<T> getWeavable(final Class<T> cls) {
        return (WeavableClass<T>) clazzes.computeIfAbsent(cls.getName(), k -> new WeavableClass<>(cls, this));
    }

    /**
     * Get enclosed {@link WeavableClass}es.
     * @return {@link Iterable}
     */
    public Iterable<WeavableClass<?>> getClasses() {
        return Collections.unmodifiableCollection(clazzes.values());
    }

    /**
     * Implement {@link Comparable}.
     * @param arg0 {@link WeavablePackage} to compare against
     * @return int per {@link Comparable#compareTo(Object)} contract
     */
    @Override
    public int compareTo(final WeavablePackage arg0) {
        return CMP.compare(this, arg0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (getTarget() == null) {
            return "Weavable default package";
        }
        return super.toString();
    }
}
