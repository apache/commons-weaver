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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

/**
 * Defines common properties and high-level management common to all commons-weaver Maven goals.
 * @since 1.3
 */
abstract class AbstractCWMojo extends AbstractMojo {
    /**
     * Marks a mojo as requiring dependencies in {@link JavaScopes#TEST} scope.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TestScope {
    }

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

    @Component
    protected RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected RepositorySystemSession repositorySystemSession;

    /**
     * Get the target directory for this prepare mojo.
     *
     * @return {@link File}
     */
    protected abstract File getTarget();

    /**
     * Execute this mojo.
     *
     * @throws MojoExecutionException in the event of failure
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        final JavaLoggingToMojoLoggingRedirector logRedirector = new JavaLoggingToMojoLoggingRedirector(getLog());
        logRedirector.activate();

        try {
            final List<String> classpath;
            try {
                classpath = createClasspath();
            } catch (DependencyResolutionException e) {
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

    private List<String> createClasspath() throws DependencyResolutionException {
        final CollectRequest collect = new CollectRequest();
        collect.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));
        collect.setRequestContext("project");
        collect.setRepositories(project.getRemoteProjectRepositories());

        for (final Dependency dependency : project.getDependencies()) {
            // guard against case where best-effort resolution for invalid models is requested:
            if (StringUtils.isAnyBlank(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())) {
                continue;
            }
            collect.addDependency(
                RepositoryUtils.toDependency(dependency, repositorySystemSession.getArtifactTypeRegistry()));
        }
        final DependencyResult dependencyResult =
            repositorySystem.resolveDependencies(repositorySystemSession, new DependencyRequest()
                .setFilter(new ScopeDependencyFilter(getExcludeScopes())).setCollectRequest(collect));

        return dependencyResult.getArtifactResults().stream().map(ar -> ar.getArtifact().getFile().getAbsolutePath())
            .distinct().collect(Collectors.toList());
    }

    private String[] getExcludeScopes() {
        return getClass().isAnnotationPresent(TestScope.class) ? ArrayUtils.EMPTY_STRING_ARRAY
            : new String[] { JavaScopes.TEST };
    }
}
