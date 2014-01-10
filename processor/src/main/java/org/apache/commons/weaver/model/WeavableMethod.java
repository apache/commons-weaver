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

import java.lang.reflect.Method;

/**
 * Represents a {@link Weavable} {@link Method}.
 *
 * @param <T> enclosing type
 */
public class WeavableMethod<T> extends WeavableExecutable<WeavableMethod<T>, Method, T, WeavableMethodParameter<T>> {

    /**
     * Create a new {@link WeavableMethod} instance.
     * @param target method
     * @param parent enclosing {@link WeavableClass}
     */
    public WeavableMethod(Method target, WeavableClass<T> parent) {
        super(target, parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?>[] getParameterTypes() {
        return getTarget().getParameterTypes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int localCompareTo(WeavableMethod<T> o) {
        int result = getTarget().getName().compareTo(o.getTarget().getName());
        return result == 0 ? super.localCompareTo(o) : result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected WeavableMethodParameter<T> createParameter(int index) {
        return new WeavableMethodParameter<T>(Integer.valueOf(index), this);
    }
}
