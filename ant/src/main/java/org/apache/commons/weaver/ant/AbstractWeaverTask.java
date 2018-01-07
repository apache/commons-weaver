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
package org.apache.commons.weaver.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Reference;

/**
 * Abstract weaver Ant task. Manages settings for filesystem-based weaving.
 */
public abstract class AbstractWeaverTask extends Task {
    private WeaverSettings settings;

    /**
     * Create a new {@link AbstractWeaverTask} instance.
     * @param project owner
     */
    protected AbstractWeaverTask(final Project project) {
        super();
        setProject(project);
    }

    /**
     * Add a nested {@link WeaverSettings}.
     * @param settings to add
     */
    public void add(final WeaverSettings settings) {
        if (this.settings != null) {
            throw new BuildException("settings already specified");
        }
        this.settings = settings;
    }

    /**
     * Get the {@link WeaverSettings} in use.
     * @return {@link WeaverSettings}
     */
    public WeaverSettings getSettings() {
        return settings;
    }

    /**
     * Set a project reference to a {@link WeaverSettings} object.
     * @param refid key
     */
    public void setSettingsRef(final String refid) {
        final WeaverSettings settings = new WeaverSettings(getProject());
        settings.setRefid(new Reference(getProject(), refid));
        add(settings);
    }
}