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

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.weaver.utils.Args;

public abstract class WeavableExecutable<SELF extends WeavableExecutable<SELF, TARGET, T, P>, TARGET extends Member, T, P extends WeavableParameter<P, SELF, TARGET, T>>
    extends NestedWeavable<SELF, TARGET, WeavableClass<T>, Class<T>> {

    private final List<P> parameters;

    protected WeavableExecutable(TARGET target, WeavableClass<T> parent) {
        super(target, parent);
        final List<P> params = new ArrayList<P>();
        for (int i = 0, sz = getParameterTypes().length; i < sz; i++) {
            params.add(createParameter(i));
        }
        parameters = Collections.unmodifiableList(params);
    }

    protected abstract P createParameter(int index);

    protected abstract Class<?>[] getParameterTypes();

    @Override
    protected int localCompareTo(SELF o) {
        return Args.compare(getParameterTypes(), o.getParameterTypes());
    }

    public P getWeavableParameter(int index) {
        return parameters.get(index);
    }

    public Iterable<P> getParameters() {
        return parameters;
    }

}
