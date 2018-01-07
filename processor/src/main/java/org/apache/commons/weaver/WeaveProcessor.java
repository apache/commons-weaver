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
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import org.apache.commons.weaver.lifecycle.WeaveLifecycle; //NOPMD used in javadoc
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.spi.Weaver;

/**
 * Implements {@link WeaveLifecycle#WEAVE}.
 */
public class WeaveProcessor extends ProcessorBase<Weaver> {

    /**
     * Create a new {@link WeaveProcessor} instance using the {@link ServiceLoader} mechanism.
     *
     * @param classpath not {@code null}
     * @param target not {@code null}
     * @param configuration not {@code null}
     */
    public WeaveProcessor(final List<String> classpath, final File target, final Properties configuration) {
        super(classpath, target, configuration, getServiceInstances(Weaver.class));
    }

    /**
     * Create a new {@link WeaveProcessor} instance.
     *
     * @param classpath not {@code null}
     * @param target not {@code null}
     * @param configuration not {@code null}
     * @param providers not (@code null}
     */
    public WeaveProcessor(final List<String> classpath, final File target, final Properties configuration,
        final Iterable<Weaver> providers) {
        super(classpath, target, configuration, providers);
    }

    /**
     * Weave classes in target directory.
     */
    public void weave() {
        if (!target.exists()) {
            log.warning(() -> String.format("Target directory %s does not exist; nothing to do!", target));
        }
        for (final Weaver weaver : providers) {
            final WeaveEnvironment env = new LocalWeaveEnvironment(target, classLoader, configuration,
                Logger.getLogger(weaver.getClass().getName()));
            weaver.process(env, finder);
        }
    }
}
