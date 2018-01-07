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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.spi.Cleaner;
import org.apache.commons.weaver.spi.Weaver;

/**
 * Scan request object describing the types of elements in which a given {@link Weaver} or {@link Cleaner} is
 * interested.
 */
public class ScanRequest {

    private final List<WeaveInterest> interests = new ArrayList<>();
    private final Set<Class<?>> supertypes = new LinkedHashSet<>();

    /**
     * Register a {@link WeaveInterest}.
     * @param interest {@link WeaveInterest} to add
     * @return {@code this}, fluently
     */
    public ScanRequest add(final WeaveInterest interest) {
        if (interest == null) {
            throw new NullPointerException();
        }
        interests.add(interest);
        return this;
    }

    /**
     * Register one or more types whose subtypes you are looking for.
     * @param types {@link Class}es to add
     * @return {@code this}, fluently
     */
    public ScanRequest addSupertypes(final Class<?>... types) {
        Collections.addAll(supertypes, Validate.noNullElements(types, "null element at [%s]"));
        return this;
    }

    /**
     * Get registered {@link WeaveInterest}s.
     * @return {@link Iterable}
     */
    public Iterable<WeaveInterest> getInterests() {
        return Collections.unmodifiableList(interests);
    }

    /**
     * Get registered {@link Class}es whose subtypes will be returned.
     * @return {@link Set}
     */
    public Set<Class<?>> getSupertypes() {
        return Collections.unmodifiableSet(supertypes);
    }

    /**
     * Learn whether this {@link ScanRequest} has been constrained. An unconstrained {@link ScanRequest} will return all
     * known types.
     * @return {@code boolean}
     * @since 1.3
     */
    public boolean isConstrained() {
        return !interests.isEmpty() || !supertypes.isEmpty();
    }
}
