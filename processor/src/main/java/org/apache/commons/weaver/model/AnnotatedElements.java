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

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy; //NOPMD used in javadoc
import java.lang.reflect.AnnotatedElement;

/**
 * Interface defining a means of iterating over a particular type of
 * {@link AnnotatedElement} as well as filtering by annotation type (including
 * annotations with {@link RetentionPolicy#CLASS} retention in addition to those
 * with {@link RetentionPolicy#RUNTIME} retention.
 * @param <T> element type
 */
public interface AnnotatedElements<T extends AnnotatedElement> extends Iterable<T> {
    /**
     * Filter by annotation type.
     * @param annotationType filter
     * @return {@link AnnotatedElements}, narrowed
     */
    AnnotatedElements<T> with(Class<? extends Annotation> annotationType);
}
