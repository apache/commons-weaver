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
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.FileArchive;

/**
 * This class picks up all Weaver plugins and process them in one go.
 */
public class WeaveProcessor {

    private static WeaveProcessor instance;

    /** List of picked up weaver plugins */
    private List<Weaver> weavers = new ArrayList<Weaver>();

    /** List of classpath entries to perform weaving on*/
    private List<File> classPathsToWeave = new ArrayList<File>();

    public static synchronized WeaveProcessor getInstance() {
        if (instance == null) {
            instance = new WeaveProcessor();
            instance.init();
        }

        return instance;
    }

    private void init() {
        ServiceLoader<Weaver> loader = ServiceLoader.load(Weaver.class);
        Iterator<Weaver> it = loader.iterator();

        while (it.hasNext()) {
            weavers.add(it.next());
        }
    }

    /**
     * All the class paths which should get weaved.
     * This e.g. contains target/classes in a typical maven installation.
     */
    public void addClassPath(File classPath) {
        classPathsToWeave.add(classPath);
    }

    /**
     * configure all Weavers.
     */
    public void configure(Map<String, Object> config) {
        for (Weaver weaver : weavers) {
            weaver.configure(config);
        }
    }

    /**
     * perform the weaving on all specified classpath entries
     */
    public void weave() {
        for (Weaver weaver : weavers) {
            weaver.preWeave();
        }

        for (Weaver weaver : weavers) {
            weave(weaver);
        }

        for (Weaver weaver : weavers) {
            weaver.postWeave();
        }
    }

    private void weave(Weaver weaver) {
        List<Class<? extends Annotation>> interest = weaver.getInterest();

        ClassLoader classLoader = new URLClassLoader(URLArray.fromFiles(classPathsToWeave));

        //X ORIGINAL AnnotationFinder annotationFinder = new AnnotationFinder(new FileArchive(classLoader, target), false);
        //X TODO this is a hack for now!
        AnnotationFinder annotationFinder = new AnnotationFinder(new FileArchive(classLoader, classPathsToWeave.get(0)), false);
        for (Class<? extends Annotation> annotation : interest) {
            List<Class<?>> annotatedClasses = annotationFinder.findAnnotatedClasses(annotation);

            for (Class<?> annotatedClass : annotatedClasses) {
                weaver.weave(annotatedClass, annotation);
            }

            List<Method> annotateMethods = annotationFinder.findAnnotatedMethods(annotation);
            for (Method annotatedMethod : annotateMethods) {
                weaver.weave(annotatedMethod, annotation);
            }
        }
    }
}
