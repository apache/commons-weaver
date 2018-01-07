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
package org.apache.commons.weaver.utils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.Consumes;
import org.apache.commons.weaver.Produces;
import org.apache.commons.weaver.spi.WeaveLifecycleProvider;

/**
 * Utility for working with {@link WeaveLifecycleProvider} types.
 */
public final class Providers {
    private enum State {
        VISITING, VISITED;
    }

    private static class SortWorker<P extends WeaveLifecycleProvider<?>> {
        /**
         * Implement {@link Providers#sort(Iterable)}.
         *
         * @param providers to sort
         * @return {@link Iterable} of {@code P}
         */
        Iterable<P> sort(final Iterable<P> providers) {
            Validate.noNullElements(providers);

            final Map<Class<? extends P>, Set<Class<? extends P>>> dependencyMap = toDependencyMap(providers);

            final Collection<Class<? extends P>> order = new LinkedHashSet<>();

            final Map<Class<? extends P>, State> stateMap = new HashMap<>();
            final Deque<Class<? extends P>> visiting = new ArrayDeque<>();

            for (final Class<? extends P> type : dependencyMap.keySet()) {
                final State state = stateMap.get(type);

                if (state == null) {
                    tsort(type, dependencyMap, stateMap, visiting, order);
                } else {
                    Validate.validState(state != State.VISITING, "Unexpected node in visiting state: %s", type);
                }
            }
            return imposeOrder(providers, order);
        }

        /**
         * Adapted from Apache Ant's target sorting mechanism.
         *
         * @param root current provider type
         * @param dependencyMap {@link Map} of provider type to dependencies
         * @param stateMap {@link Map} of current visitation state
         * @param visiting {@link Deque} used as a stack
         * @param target destination {@link Collection} which must preserve order
         */
        private void tsort(final Class<? extends P> root,
            final Map<Class<? extends P>, Set<Class<? extends P>>> dependencyMap,
            final Map<Class<? extends P>, State> stateMap, final Deque<Class<? extends P>> visiting,
            final Collection<Class<? extends P>> target) {

            stateMap.put(root, State.VISITING);
            visiting.push(root);

            for (final Class<? extends P> dependency : dependencyMap.get(root)) {
                final State state = stateMap.get(dependency);
                if (state == State.VISITED) {
                    continue;
                }
                Validate.validState(state == null, "Circular dependency: %s of %s", dependency.getName(),
                    root.getName());
                tsort(dependency, dependencyMap, stateMap, visiting, target);
            }
            final Class<? extends P> top = visiting.pop();
            Validate.validState(top == root, "Stack out of balance: expected %s, found %s", root.getName(),
                top.getName());

            stateMap.put(root, State.VISITED);
            target.add(root);
        }

        /**
         * Read any {@link Produces} annotation associated with {@code providerClass}, designating types before which it
         * should be invoked.
         *
         * @param providerClass
         * @return {@link Class}[]
         */
        private Class<? extends P>[] producedBy(final Class<? extends P> providerClass) {
            Validate.notNull(providerClass);
            final Produces produces = providerClass.getAnnotation(Produces.class);
            if (produces == null || produces.value().length == 0) {
                @SuppressWarnings("unchecked")
                final Class<? extends P>[] empty = (Class<? extends P>[]) ArrayUtils.EMPTY_CLASS_ARRAY;
                return empty;
            }
            @SuppressWarnings("unchecked")
            final Class<? extends P>[] result = (Class<? extends P>[]) produces.value();
            return result;
        }

        /**
         * Read any {@link Consumes} annotation associated with {@code providerClass} as dependencies.
         *
         * @param providerClass
         * @return {@link Class}[]
         */
        private Class<? extends P>[] consumedBy(final Class<? extends P> providerClass) {
            Validate.notNull(providerClass);
            final Consumes consumes = providerClass.getAnnotation(Consumes.class);
            if (consumes == null || consumes.value().length == 0) {
                @SuppressWarnings("unchecked")
                final Class<? extends P>[] empty = (Class<? extends P>[]) ArrayUtils.EMPTY_CLASS_ARRAY;
                return empty;
            }
            @SuppressWarnings("unchecked")
            final Class<? extends P>[] result = (Class<? extends P>[]) consumes.value();
            return result;
        }

        /**
         * Create a {@link Map} of provider type to dependency types.
         *
         * @param providers to inspect
         * @return {@link Map}
         */
        private Map<Class<? extends P>, Set<Class<? extends P>>> toDependencyMap(final Iterable<P> providers) {

            final Map<Class<? extends P>, Set<Class<? extends P>>> result = new HashMap<>();

            for (final WeaveLifecycleProvider<?> provider : providers) {
                @SuppressWarnings("unchecked")
                final Class<? extends P> type = (Class<? extends P>) provider.getClass();
                Collections.addAll(result.computeIfAbsent(type, k -> new HashSet<>()), consumedBy(type));

                for (final Class<? extends P> dependent : producedBy(type)) {
                    result.computeIfAbsent(dependent, k -> new HashSet<>()).add(type);
                }
            }
            return result;
        }

        /**
         * Order providers.
         *
         * @param providers to sort
         * @param order to respect
         * @return reordered providers
         */
        private Iterable<P> imposeOrder(final Iterable<P> providers, final Iterable<Class<? extends P>> order) {

            final Set<P> result = new LinkedHashSet<>();

            for (final Class<? extends P> type : order) {
                for (final P provider : providers) {
                    if (type.isInstance(provider)) {
                        result.add(provider);
                    }
                }
            }
            return Collections.unmodifiableSet(result);
        }
    }

    /**
     * Sort the specified providers with respect to declared {@link Consumes} and {@link Produces} annotations.
     *
     * @param <P> the {@link WeaveLifecycleProvider} type
     * @param providers to sort
     * @return {@link Iterable} of {@code P}
     */
    public static <P extends WeaveLifecycleProvider<?>> Iterable<P> sort(final Iterable<P> providers) {
        return new SortWorker<P>().sort(providers);
    }

    private Providers() {
    }
}
