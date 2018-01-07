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
package org.apache.commons.weaver.privilizer;

import java.lang.annotation.ElementType;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Weaver;

/**
 * Privilizer {@link Weaver} implementation.
 */
public class PrivilizerWeaver implements Weaver {
    @Override
    public boolean process(final WeaveEnvironment weaveEnvironment, final Scanner scanner) {
        final Privilizer privilizer = new Privilizer(weaveEnvironment);

        final Set<Class<?>> privilizedTypes = new LinkedHashSet<>();

        // handle blueprints:
        for (final WeavableClass<?> type : scanner.scan(
            new ScanRequest().add(WeaveInterest.of(Privilizing.class, ElementType.TYPE))).getClasses()) {

            final Class<?> target = type.getTarget();
            if (privilizedTypes.add(target) && validateRequest(privilizer, type)) {
                privilizer.blueprint(target, type.getAnnotation(Privilizing.class));
            }
        }

        // handle remaining classes declaring @Privileged methods:

        for (final WeavableClass<?> type : scanner.scan(
            new ScanRequest().add(WeaveInterest.of(Privileged.class, ElementType.METHOD))).getClasses()) {
            final Class<?> target = type.getTarget();
            if (privilizedTypes.add(target) && validateRequest(privilizer, type)) {
                privilizer.privilize(target);
            }
        }
        return !privilizedTypes.isEmpty();
    }

    /**
     * Validate a weaving request for a given target type.
     * @param privilizer whose configuration to consult
     * @param type target
     * @return whether weaving should proceed
     * @throws IllegalStateException if class has already been woven with some other policy
     */
    private boolean validateRequest(final Privilizer privilizer, final WeavableClass<?> type) {
        final Privilized marker = type.getAnnotation(Privilized.class);
        if (marker == null) {
            return privilizer.policy != Policy.NEVER;
        }
        Validate.validState(privilizer.policy.name().equals(marker.value()), "%s already privilized with policy %s",
            type.getTarget().getName(), marker.value());

        return false;
    }
}
