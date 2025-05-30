/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Goal to clean woven classes.
 */
@Mojo(
    name = "prepare",
    defaultPhase = LifecyclePhase.INITIALIZE,
    requiresDependencyCollection = ResolutionScope.RUNTIME_PLUS_SYSTEM,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM
)
public class PrepareMojo extends AbstractPrepareMojo {
    /**
     * {@link Build#getOutputDirectory()}.
     */
    @Parameter(readonly = true, required = true, defaultValue = "${project.build.outputDirectory}")
    protected File target;

    /**
     * {@inheritDoc}
     */
    @Override
    protected File getTarget() {
        return target;
    }
}
