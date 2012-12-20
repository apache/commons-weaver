/*
 *  Copyright the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.commons.weaver.maven;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;


/**
 * Goal to weave classes.
 */
@Mojo(name = "weave", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyCollection = ResolutionScope.COMPILE)
public class WeaveMojo extends AbstractWeaveMojo {

    @Parameter(readonly = true, required = true, defaultValue = "${project.compileClasspathElements}")
    protected List<String> classpath;

    @Parameter(readonly = true, required = true, defaultValue = "${project.build.outputDirectory}")
    protected File target;

    @Parameter(readonly = false, required = true, defaultValue = "PACKAGE")
    protected AccessLevel accessLevel;

    @Override
    protected List<String> getClasspath() {
        return classpath;
    }

    @Override
    protected File getTarget() {
        return target;
    }

    @Override
    protected AccessLevel getAccessLevel() {
        return accessLevel;
    }
    @Override
    public void execute() throws MojoExecutionException
    {
        try {
            createWeaver().weaveAll();
        } catch (Exception e) {
            throw new MojoFailureException("failed", e);
        }
    }
}
