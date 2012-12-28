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
package org.apache.commons.weaver.ant;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.weaver.WeaveProcessor;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;

/**
 * Privileged method weaving Ant task.
 */
public class WeaveTask  extends Task {

    private File target;
    private Path classpath;
    private String classpathref;
    private Properties weaverConfig;


    @Override
    public void execute() throws BuildException {
        try {
            WeaveProcessor wp = WeaveProcessor.getInstance();
            wp.configure(getClassPathEntries(), target, weaverConfig);
            wp.weave();
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    protected File getTarget() {
        return target;
    }

    public void setTarget(File target) {
        this.target = target;
    }

    protected String getClasspathref() {
        return classpathref;
    }

    public void setClasspathRef(String classpathref) {
        this.classpathref = classpathref;
    }

    protected List<String> getClassPathEntries() {
        final Path p = new Path(getProject());
        final Path cp = getClasspath();
        if (cp != null) {
            p.add(cp);
        }
        p.add(Path.systemClasspath);

        return Arrays.asList(p.list());
    }

    protected Path getClasspath() {
        if (classpath == null) {
            if (getClasspathref() != null) {
                Path ref = new Path(getProject());
                ref.setRefid(new Reference(getProject(), getClasspathref()));
                return ref;
            }
        } else if (StringUtils.isNotBlank(getClasspathref())) {
            throw new BuildException("Only one of classpathref|classpath is permitted.");
        }
        return classpath;
    }

    public void setClasspath(Path classpath) {
        if (this.classpath != null) {
            throw new BuildException("classpath already set");
        }
        this.classpath = classpath;
    }

    public Properties getWeaverConfig() {
        return weaverConfig;
    }

    public void setWeaverConfig(Properties weaverConfig) {
        this.weaverConfig = weaverConfig;
    }
}
