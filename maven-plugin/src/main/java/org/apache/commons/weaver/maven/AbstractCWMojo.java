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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Defines common properties and high-level management common to all commons-weaver Maven goals.
 */
abstract class AbstractCWMojo extends AbstractMojo {

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

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    /**
     * Get the classpath for this prepare mojo.
     * @return {@link List} of {@link String}
     * @throws DependencyResolutionRequiredException
     */
    protected abstract List<String> getClasspath() throws DependencyResolutionRequiredException;

    /**
     * Get the target directory for this prepare mojo.
     * @return {@link File}
     */
    protected abstract File getTarget();

    /**
     * Execute this mojo.
     * @throws MojoExecutionException in the event of failure
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        final JavaLoggingToMojoLoggingRedirector logRedirector = new JavaLoggingToMojoLoggingRedirector(getLog());
        logRedirector.activate();

        project.setArtifactFilter(new ArtifactFilter() {

            @Override
            public boolean include(Artifact artifact) {
                return true;
            }
        });
        try {
            final List<String> classpath;
            try {
                classpath = getClasspath();
            } catch (DependencyResolutionRequiredException e) {
                throw new MojoExecutionException("Error getting classpath artifacts", e);
            }
            final File target = getTarget();
            final Properties config = weaverConfig == null ? new Properties() : weaverConfig;
            
            getLog().debug(String.format("classpath=%s%ntarget=%s%nconfig=%s", classpath, target, config));

            doExecute(target, classpath, config);
        } finally {
            logRedirector.deactivate();
        }
    }

    protected abstract void doExecute(File target, List<String> classpath, Properties config)
        throws MojoExecutionException, MojoFailureException;

}
