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
package org.apache.commons.weaver.privilizer.ant;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.privilizer.weaver.AccessLevel;
import org.apache.commons.weaver.privilizer.weaver.FilesystemPrivilizer;
import org.apache.commons.weaver.privilizer.weaver.URLArray;
import org.apache.commons.weaver.privilizer.weaver.Privilizer.Log;
import org.apache.commons.weaver.privilizer.weaver.Privilizer.Policy;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;


/**
 * abstract privileged method weaving Ant task.
 */
public abstract class PrivilegedTask extends Task {
    private Policy policy;
    private File target;
    private Path classpath;
    private String classpathref;
    private AccessLevel accessLevel;

    @Override
    public abstract void execute() throws BuildException;

    protected FilesystemPrivilizer createWeaver() {
        Validate.notNull(getTarget(), "target");

        final Path p = new Path(getProject());
        final Path cp = getClasspath();
        if (cp != null) {
            p.add(cp);
        }
        p.add(Path.systemClasspath);

        log("Using " + p.toString(), Project.MSG_DEBUG);
        final ClassLoader loader = new URLClassLoader(URLArray.fromPaths(Arrays.asList(p.list())));

        return new FilesystemPrivilizer(getPolicy(), loader, getTarget()) {
            @Override
            protected boolean permitMethodWeaving(AccessLevel accessLevel) {
                return getAccessLevel().compareTo(accessLevel) <= 0;
            }
        }.loggingTo(new Log() {

            @Override
            public void info(String message) {
                log(message);
            }

            @Override
            public void error(String message) {
                log(message, Project.MSG_ERR);
            }

            @Override
            public void debug(String message) {
                log(message, Project.MSG_DEBUG);
            }

            @Override
            public void verbose(String message) {
                log(message, Project.MSG_VERBOSE);
            }

            @Override
            public void warn(String message) {
                log(message, Project.MSG_WARN);
            }
        });
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

    protected Policy getPolicy() {
        return ObjectUtils.defaultIfNull(policy, Policy.DYNAMIC);
    }

    /**
     * Set the weaving policy (default DYNAMIC i.e. checks SecurityManager on
     * each invocation)
     * 
     * @param policy
     */
    public void setPolicy(Policy policy) {
        this.policy = policy;
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

    protected AccessLevel getAccessLevel() {
        return ObjectUtils.defaultIfNull(accessLevel, AccessLevel.PACKAGE);
    }

    /**
     * Set "minimum" access level to be woven (default PACKAGE).
     * 
     * @param accessLevel
     */
    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
}
