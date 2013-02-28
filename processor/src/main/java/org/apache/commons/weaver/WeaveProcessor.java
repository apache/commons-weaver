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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.Annotated;
import org.apache.xbean.finder.Parameter;
import org.apache.xbean.finder.archive.FileArchive;

/**
 * This class picks up all Weaver plugins and process them in one go.
 */
public class WeaveProcessor {

    private static WeaveProcessor instance;

    /**
     * The classpath which will be used to look up cross references during
     * weaving.
     */
    private List<String> classPath;

    /**
     * The actual path which gets weaved. All the classes in this path will get
     * weaved. The weaved classes will replace the original classes.
     */
    private File target;

    /** List of picked up weaver plugins */
    private List<Weaver> weavers = new ArrayList<Weaver>();

    public static synchronized WeaveProcessor getInstance() {
        if (instance == null) {
            instance = new WeaveProcessor();
            instance.init();
        }
        return instance;
    }

    private WeaveProcessor() {
    }

    private void init() {
        ServiceLoader<Weaver> loader = ServiceLoader.load(Weaver.class);
        Iterator<Weaver> it = loader.iterator();

        while (it.hasNext()) {
            weavers.add(it.next());
        }
    }

    /**
     * Configure all Weavers.
     * 
     * @param classPath
     *            the classpath to look up cross-references in during weaving
     * @param target
     *            the File path where the classes to weave reside
     * @param config
     *            additional configuration for all plugins.
     */
    public void configure(List<String> classPath, File target, Properties config) {
        this.classPath = classPath;
        this.target = target;

        for (Weaver weaver : weavers) {
            weaver.configure(classPath, target, config);
        }
    }

    /**
     * perform the weaving on all specified classpath entries
     */
    public void weave() {
        final ClassLoader classLoader = new URLClassLoader(URLArray.fromPaths(classPath));
        final Finder finder = new Finder(new FileArchive(classLoader, target));
        for (Weaver weaver : weavers) {
            weave(finder, weaver);
        }
    }

    private void weave(Finder finder, Weaver weaver) {
        final ScanResult result = new ScanResult();

        for (WeaveInterest interest : weaver.getScanRequest().getInterests()) {
            switch (interest.target) {
            case PACKAGE:
                for (Annotated<Package> pkg : finder.withAnnotations().findAnnotatedPackages(interest.annotationType)) {
                    result.getWeavable(pkg.get()).addAnnotations(pkg.getAnnotation(interest.annotationType));
                }
            case TYPE:
                for (Annotated<Class<?>> type : finder.withAnnotations().findAnnotatedClasses(interest.annotationType)) {
                    result.getWeavable(type.get()).addAnnotations(type.getAnnotation(interest.annotationType));
                }
                break;
            case METHOD:
                for (Annotated<Method> method : finder.withAnnotations().findAnnotatedMethods(interest.annotationType)) {
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
                for (Annotated<Parameter<Method>> parameter : finder.withAnnotations().findAnnotatedMethodParameters(
                    interest.annotationType)) {
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
