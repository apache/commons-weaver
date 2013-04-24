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
package org.apache.commons.weaver;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.Annotated;
import org.apache.xbean.finder.Parameter;
import org.apache.xbean.finder.archive.FileArchive;

/**
 * This class discovers and invokes available {@link Weaver} plugins.
 */
public class WeaveProcessor {

    /** List of picked up weaver plugins */
    private static List<Weaver> WEAVERS = new ArrayList<Weaver>();

    static {
        List<Weaver> weavers = new ArrayList<Weaver>();
        for (Weaver w : ServiceLoader.load(Weaver.class)) {
            weavers.add(w);
        }
        WEAVERS = Collections.unmodifiableList(weavers);
    }

    /**
     * The classpath which will be used to look up cross references during weaving.
     */
    private final List<String> classpath;

    /**
     * The actual path to be woven, replacing any affected classes.
     */
    private final File target;

    /**
     * Properties for configuring discovered plugin modules.
     */
    private final Properties configuration;

    /**
     * Create a new {@link WeaveProcessor} instance.
     * 
     * @param classpath not {@code null}
     * @param target not {@code null}
     * @param configuration not {@code null}
     */
    public WeaveProcessor(List<String> classpath, File target, Properties configuration) {
        super();
        this.classpath = Validate.notNull(classpath, "classpath");
        this.target = Validate.notNull(target, "target");
        this.configuration = Validate.notNull(configuration, "configuration");
    }

    /**
     * Weave classes in target directory.
     */
    public void weave() {
        final ClassLoader classLoader = new URLClassLoader(URLArray.fromPaths(classpath));
        final Finder finder = new Finder(new FileArchive(classLoader, target));
        for (Weaver weaver : WEAVERS) {
            weave(finder, weaver);
        }
    }

    private void weave(final Finder finder, final Weaver weaver) {
        weaver.configure(classpath, target, configuration);
        final ScanResult result = new ScanResult();

        for (WeaveInterest interest : weaver.getScanRequest().getInterests()) {
            switch (interest.target) {
                case PACKAGE:
                    for (Annotated<Package> pkg : finder.withAnnotations().findAnnotatedPackages(
                        interest.annotationType)) {
                        result.getWeavable(pkg.get()).addAnnotations(pkg.getAnnotation(interest.annotationType));
                    }
                case TYPE:
                    for (Annotated<Class<?>> type : finder.withAnnotations().findAnnotatedClasses(
                        interest.annotationType)) {
                        result.getWeavable(type.get()).addAnnotations(type.getAnnotation(interest.annotationType));
                    }
                    break;
                case METHOD:
                    for (Annotated<Method> method : finder.withAnnotations().findAnnotatedMethods(
                        interest.annotationType)) {
                        result.getWeavable(method.get()).addAnnotations(method.getAnnotation(interest.annotationType));
                    }
                    break;
                case CONSTRUCTOR:
                    for (Annotated<Constructor<?>> cs : finder.withAnnotations().findAnnotatedConstructors(
                        interest.annotationType)) {
                        result.getWeavable(cs.get()).addAnnotations(cs.getAnnotation(interest.annotationType));
                    }
                    break;
                case FIELD:
                    for (Annotated<Field> fld : finder.withAnnotations().findAnnotatedFields(interest.annotationType)) {
                        result.getWeavable(fld.get()).addAnnotations(fld.getAnnotation(interest.annotationType));
                    }
                    break;
                case PARAMETER:
                    for (Annotated<Parameter<Method>> parameter : finder.withAnnotations()
                        .findAnnotatedMethodParameters(interest.annotationType)) {
                        result.getWeavable(parameter.get().getDeclaringExecutable())
                            .getWeavableParameter(parameter.get().getIndex())
                            .addAnnotations(parameter.getAnnotation(interest.annotationType));
                    }
                    for (Annotated<Parameter<Constructor<?>>> parameter : finder.withAnnotations()
                        .findAnnotatedConstructorParameters(interest.annotationType)) {
                        result.getWeavable(parameter.get().getDeclaringExecutable())
                            .getWeavableParameter(parameter.get().getIndex())
                            .addAnnotations(parameter.getAnnotation(interest.annotationType));
                    }
                    break;
                default:
                    // should we log something?
                    break;
            }
        }
        weaver.process(result);
    }
}
