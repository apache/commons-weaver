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

/**
 * Describes a {@link Weavable} that lives inside some other {@link Weavable}.
 * @param <SELF> own type
 * @param <TARGET> weavable target type
 * @param <PARENT> enclosing weavable type
 * @param <PARENT_TARGET> parent target type
 */
public abstract class NestedWeavable
    <SELF extends NestedWeavable<SELF, TARGET, PARENT, PARENT_TARGET>,
    TARGET,
    PARENT extends Weavable<PARENT, PARENT_TARGET>,
    PARENT_TARGET>
    extends Weavable<SELF, TARGET> {

    private final PARENT parent;

    /**
     * Create a new {@link NestedWeavable} instance.
     * @param target element
     * @param parent enclosing
     */
    protected NestedWeavable(TARGET target, PARENT parent) {
        super(target);
        this.parent = parent;
    }

    /**
     * Get the parent.
     * @return {@code PARENT}
     */
    public PARENT getParent() {
        return parent;
    }

    /**
     * Implement {@link Comparable}.
     * @param o {@code SELF}
     * @return int per {@link Comparable#compareTo(Object)} contract
     */
    @Override
    public final int compareTo(SELF o) {
        int result = getParent().compareTo(o.getParent());
        return result == 0 ? localCompareTo(o) : result;
    }

    /**
     * Compare against {@code o} without respect to {@link #getParent()}.
     * @param o SELF{@code SELF}
     * @return int per {@link Comparable#compareTo(Object)} contract
     */
    protected abstract int localCompareTo(SELF o);
}
