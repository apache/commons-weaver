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
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.spi.Cleaner;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.archive.FileArchive;

/**
 * This class discovers and invokes available {@link Cleaner} plugins.
 */
public class CleanProcessor {
    private static final Logger LOG = Logger.getLogger(CleanProcessor.class.getName());

    /**
     * List of picked up cleaner plugins.
     */
    private static final List<Cleaner> CLEANERS;

    static {
        List<Cleaner> cleaners = new ArrayList<Cleaner>();
        for (Cleaner c : ServiceLoader.load(Cleaner.class)) {
            cleaners.add(c);
        }
        CLEANERS = Collections.unmodifiableList(cleaners);
    }

    /**
     * The classpath which will be used to look up cross references during cleaning.
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
     * Create a new {@link CleanProcessor} instance.
     *
     * @param classpath not {@code null}
     * @param target not {@code null}
     * @param configuration not {@code null}
     */
    public CleanProcessor(List<String> classpath, File target, Properties configuration) {
        super();
        this.classpath = Validate.notNull(classpath, "classpath");
        this.target = Validate.notNull(target, "target");
        Validate.isTrue(!target.exists() || target.isDirectory(), "%s is not a directory", target);
        this.configuration = Validate.notNull(configuration, "configuration");
    }

    /**
     * Clean specified targets.
     */
    public void clean() {
        if (!target.exists()) {
            LOG.warning("Target directory " + target + " does not exist; nothing to do!");
        }
        final Set<String> finderClasspath = new LinkedHashSet<String>();
        finderClasspath.add(target.getAbsolutePath());
        finderClasspath.addAll(classpath);
        final ClassLoader classLoader = new URLClassLoader(URLArray.fromPaths(finderClasspath));
        final Finder finder = new Finder(new FileArchive(classLoader, target));
        for (Cleaner cleaner : CLEANERS) {
            final WeaveEnvironment env =
                new LocalWeaveEnvironment(target, classLoader, configuration, Logger.getLogger(cleaner.getClass()
                    .getName()));
            cleaner.clean(env, finder);
        }
    }
}
