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

/**
 * Represents the parameter of an executable.
 *
 * @param <SELF> own type
 * @param <PARENT> {@link WeavableExecutable} type
 * @param <PARENT_TARGET> target executable of parent
 * @param <T> executable's owning type
 */
public abstract class WeavableParameter
    <SELF extends WeavableParameter<SELF, PARENT, PARENT_TARGET, T>,
    PARENT extends WeavableExecutable<PARENT, PARENT_TARGET, T, SELF>,
    PARENT_TARGET extends Member,
    T>
    extends NestedWeavable<SELF, Integer, PARENT, PARENT_TARGET> {

    /**
     * Create a new {@link WeavableParameter} instance.
     * @param target index
     * @param parent executable
     */
    protected WeavableParameter(final Integer target, final PARENT parent) {
        super(target, parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int localCompareTo(final SELF obj) {
        return getTarget().compareTo(getTarget());
    }
}
