/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.commons.weaver.model;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;

/**
 * Weave interest composed of annotation type and target element type.
 */
public final class WeaveInterest {

    /**
     * Gets a {@link WeaveInterest}.
     * @param annotationType observed annotation type
     * @param target attached element type
     * @return {@link WeaveInterest}
     */
    public static WeaveInterest of(final Class<? extends Annotation> annotationType, final ElementType target) {
        return new WeaveInterest(annotationType, target);
    }

    /**
     * Observed annotation type.
     */
    public final Class<? extends Annotation> annotationType;

    /**
     * Attached element type.
     */
    public final ElementType target;

    private WeaveInterest(final Class<? extends Annotation> annotationType, final ElementType target) {
        this.annotationType = annotationType;
        this.target = target;
    }
}
