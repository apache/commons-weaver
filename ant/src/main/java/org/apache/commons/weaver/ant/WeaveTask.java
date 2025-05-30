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
package org.apache.commons.weaver.ant;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.WeaveProcessor;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * Weave Ant task.
 */
public class WeaveTask extends AbstractWeaverTask {
    /**
     * Create a new {@link WeaveTask} instance.
     * @param project owner
     */
    public WeaveTask(final Project project) {
        super(project);
    }

    /**
     * Execute the weave task.
     */
    @Override
    public void execute() {
        try {
            final WeaverSettings settings = Validate.notNull(getSettings(), "settings");
            new WeaveProcessor(settings.getClasspathEntries(), settings.getTarget(), settings.getProperties()).weave();
        } catch (final Exception e) {
            throw new BuildException(e);
        }
    }
}
