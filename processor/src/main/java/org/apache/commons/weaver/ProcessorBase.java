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
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.lifecycle.WeaveLifecycle;
import org.apache.commons.weaver.spi.WeaveLifecycleProvider;
import org.apache.commons.weaver.utils.Providers;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.archive.FileArchive;

/**
 * Base implementor of a {@link WeaveLifecycle} stage.
 *
 * @param <P> managed {@link WeaveLifecycleProvider} type
 * @since 1.2
 */
class ProcessorBase<P extends WeaveLifecycleProvider<?>> {

    /**
     * Use the {@link ServiceLoader} to discover available {@code type} implementations.
     *
     * @param type not {@code null}
     * @return {@link Iterable} of {@code T}
     */
    static <T> Iterable<T> getServiceInstances(final Class<T> type) {
        Validate.notNull(type);
        final List<T> result = new ArrayList<>();
        final ClassLoader typeLoader = type.getClassLoader();
        if (!Thread.currentThread().getContextClassLoader().equals(typeLoader)) {
            for (final T t : ServiceLoader.load(type, typeLoader)) {
                result.add(t);
            }
        }
        for (final T t : ServiceLoader.load(type)) {
            result.add(t);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Logger instance.
     */
    protected final Logger log = Logger.getLogger(getClass().getName());

    /**
     * The classpath which will be used to look up cross references during weaving.
     */
    protected final List<String> classpath;

    /**
     * The actual path to be woven, replacing any affected classes.
     */
    protected final File target;

    /**
     * Properties for configuring discovered plugin modules.
     */
    protected final Properties configuration;

    /**
     * The managed {@link WeaveLifecycleProvider}es.
     */
    protected final Iterable<P> providers;

    /**
     * {@link ClassLoader} representing {@link #classpath}.
     */
    protected final ClassLoader classLoader;

    /**
     * {@link Finder} instance using for weaving.
     */
    protected final Finder finder;

    /**
     * Create a new {@link ProcessorBase} instance.
     *
     * @param classpath not {@code null}
     * @param target not {@code null}
     * @param configuration not {@code null}
     * @param providers not empty
     */
    protected ProcessorBase(final List<String> classpath, final File target, final Properties configuration,
        final Iterable<P> providers) {
        this.classpath = Validate.notNull(classpath, "classpath");
        this.target = Validate.notNull(target, "target");
        Validate.isTrue(!target.exists() || target.isDirectory(), "%s is not a directory", target);
        this.configuration = Validate.notNull(configuration, "configuration");
        this.providers = Providers.sort(providers);
        this.classLoader = createClassLoader();
        this.finder = new Finder(new FileArchive(classLoader, target));
    }

    private ClassLoader createClassLoader() {
        final Set<String> finderClasspath = new LinkedHashSet<>();
        finderClasspath.add(target.getAbsolutePath());
        finderClasspath.addAll(classpath);
        return new URLClassLoader(URLArray.fromPaths(finderClasspath));
    }
}
