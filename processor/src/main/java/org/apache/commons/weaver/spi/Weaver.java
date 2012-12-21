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
package org.apache.commons.weaver.spi;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * An implementation of a 'Weaver' takes care about
 * certain weaving jobs and will perform the byte code
 * enhancement in the classes.
 *
 * TODO: we might enhance this SPI to gather upfront information about what needs to get scanned at all!
 */
public interface Weaver
{
    /**
     * This is for now a simple way to configure any weaver.
     * Any configuration property of a weaver should start with it's 'name'
     * e.g. 'privilizer'
     * @param classPath the classpath to look up cross-references in during weaving
     * @param target the File path where the classes to weave reside
     * @param config additional configuration for all plugins.
     */
    void configure(List<String> classPath, File target, Map<String, Object> config);

    /**
     * A Weaver must return a List of Annotations he is interested in.
     */
    List<Class<? extends Annotation>> getInterest();

    /**
     * This will get invoked before any weaver did run
     */
    void preWeave();

    /**
     * Perform weaving on the given class for any class which has one of the required annotations.
     * If there is nothing to do, then just go on.
     *
     * @return <code>true</code> if some bytecode has been changed
     */
    boolean weave(Class classToWeave, Class<? extends Annotation> processingAnnotation);

    /**
     * Perform weaving on the given class for any class which has one of the required annotations.
     * If there is nothing to do, then just go on.
     *
     * @return <code>true</code> if some bytecode has been changed
     */
    boolean weave(Method methodToWeave, Class<? extends Annotation> processingAnnotation);

    /**
     * This method will get invoked after all {@link #weave(Class, Class)} and
     * {@link #weave(java.lang.reflect.Method, Class)} methods got invoked
     * for all classes on every weaver.
     */
    void postWeave();
}
