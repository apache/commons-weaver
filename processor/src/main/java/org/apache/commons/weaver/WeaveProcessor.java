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
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.archive.FileArchive;

/**
 * This class discovers and invokes available {@link Weaver} plugins.
 */
public class WeaveProcessor {

    /** List of picked up weaver plugins */
    private static final List<Weaver> WEAVERS;

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
            final WeaveEnvironment env =
                new WeaveEnvironment(target, classLoader, configuration, Logger.getLogger(weaver.getClass().getName()));
            weaver.process(env, finder);
        }
    }
}
