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
package org.apache.commons.weaver.maven;

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.apache.commons.weaver.WeaveProcessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Defines common properties.
 */
public abstract class AbstractWeaveMojo extends AbstractMojo {

    /**
     * {@code verbose} parameter.
     */
    @Parameter(defaultValue = "false")
    protected boolean verbose;

    /**
     * {@code weaver.config} parameter.
     */
    @Parameter(property = "weaver.config", required = false)
    protected Properties weaverConfig;

    /**
     * Get the classpath for this weave mojo.
     * @return {@link List} of {@link String}
     */
    protected abstract List<String> getClasspath();

    /**
     * Get the target directory for this weave mojo.
     * @return {@link File}
     */
    protected abstract File getTarget();

    /**
     * Execute this mojo.
     * @throws MojoExecutionException in the event of failure
     */
    @Override
    public void execute() throws MojoExecutionException {
        final JavaLoggingToMojoLoggingRedirector logRedirector = new JavaLoggingToMojoLoggingRedirector(getLog());
        logRedirector.activate();

        final List<String> classpath = getClasspath();
        final File target = getTarget();
        final Properties config = weaverConfig == null ? new Properties() : weaverConfig;

        getLog().debug(String.format("classpath=%s%ntarget=%s%nconfig=%s", classpath, target, config));

        try {
            final WeaveProcessor weaveProcessor = new WeaveProcessor(classpath, target, config);
            weaveProcessor.weave();
        } catch (Exception e) {
            throw new MojoExecutionException("weaving failed due to " + e.getMessage(), e);
        } finally {
            logRedirector.deactivate();
        }
    }

}
