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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PropertySet;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.PropertySet.BuiltinPropertySetName;

/**
 * Standalone weaver settings datatype. Handles:
 * <ul>
 * <li>{@code target} attribute - {@link File}</li>
 * <li>{@code classpath} attribute - {@link Path} (incompatible with {@code classpathref})</li>
 * <li>{@code classpathref} attribute - {@link String} (incompatible with {@code classpath})</li>
 * <li>{@code includeSystemClasspath} attribute - {@code boolean}</li>
 * <li>nested {@code propertyset} - {@link PropertySet}</li>
 * <li>nested {@code properties} - {@link InlineProperties}</li>
 * </ul>
 * {@code propertyset} and {@code properties} are merged, with the latter taking precedence.
 */
public class WeaverSettings extends DataType {
    private File target;
    private Path classpath;
    private String classpathref;
    private PropertySet propertySet;
    private InlineProperties inlineProperties;
    private boolean includeSystemClasspath;

    /**
     * Create a new {@link WeaverSettings} object.
     * @param project owner
     */
    public WeaverSettings(final Project project) {
        super();
        setProject(project);
    }

    /**
     * Get the {@code target} directory.
     * @return {@link File}
     */
    public File getTarget() {
        if (isReference()) {
            return getRef().getTarget();
        }
        return target;
    }

    /**
     * Set the {@code target} directory.
     * @param target {@link File}
     */
    public void setTarget(final File target) {
        if (isReference()) {
            throw tooManyAttributes();
        }
        this.target = target;
    }

    /**
     * Get the {@code classpathref}.
     * @return {@link String}
     */
    public String getClasspathref() {
        if (isReference()) {
            return getRef().getClasspathref();
        }
        return classpathref;
    }

    /**
     * Set the {@code classpathref}.
     * @param classpathref {@link String}
     */
    public void setClasspathRef(final String classpathref) {
        if (isReference()) {
            throw tooManyAttributes();
        }
        this.classpathref = classpathref;
    }

    /**
     * Return the effective classpath as a {@link List} of {@link String}
     * filesystem paths. If {@link #includeSystemClasspath}, system classpath will be appended.
     * @return {@link List} of {@link String}
     */
    public List<String> getClasspathEntries() {
        final Path path = new Path(getProject());
        final Path classpath = getClasspath();
        if (classpath != null) {
            path.add(classpath);
        }
        if (includeSystemClasspath) {
            path.add(Path.systemClasspath);
        }
        return Arrays.asList(path.list());
    }

    /**
     * Get the {@code classpath}.
     * @return {@link Path}
     */
    public Path getClasspath() {
        if (isReference()) {
            return getRef().getClasspath();
        }
        if (classpath == null) {
            if (getClasspathref() != null) {
                final Path ref = new Path(getProject());
                ref.setRefid(new Reference(getProject(), getClasspathref()));
                return ref;
            }
        } else if (StringUtils.isNotBlank(getClasspathref())) {
            throw new BuildException("Only one of classpathref|classpath is permitted.");
        }
        return classpath;
    }

    /**
     * Set the {@code classpath}.
     * @param classpath {@link Path}
     */
    public void setClasspath(final Path classpath) {
        if (isReference()) {
            throw tooManyAttributes();
        }
        if (this.classpath != null) {
            throw new BuildException("classpath already set");
        }
        this.classpath = classpath;
    }

    /**
     * Create the nested {@code properties}.
     * @return {@link InlineProperties}
     */
    public InlineProperties createProperties() {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        if (inlineProperties != null) {
            throw new BuildException("properties already specified");
        }
        inlineProperties = new InlineProperties();
        return inlineProperties;
    }

    /**
     * Create a nested {@code propertyset}.
     * @return {@link PropertySet}
     */
    public PropertySet createPropertySet() {
        if (isReference()) {
            throw noChildrenAllowed();
        }
        if (propertySet != null) {
            throw new BuildException("propertyset already specified");
        }
        propertySet = new PropertySet();
        propertySet.setProject(getProject());
        return propertySet;
    }

    /**
     * Set whether to include the system classpath.
     * @param includeSystemClasspath the includeSystemClasspath to set
     * @since 1.3
     * @see Path#systemClasspath
     */
    public void setIncludeSystemClasspath(final boolean includeSystemClasspath) {
        this.includeSystemClasspath = includeSystemClasspath;
    }

    /**
     * Merge nested {@code propertyset} and {@code properties}; latter takes precedence.
     * @return {@link Properties}
     */
    public Properties getProperties() {
        if (isReference()) {
            return getRef().getProperties();
        }
        if (propertySet == null && inlineProperties == null) {
            createPropertySet().appendBuiltin(
                (BuiltinPropertySetName) EnumeratedAttribute.getInstance(BuiltinPropertySetName.class, "all"));
        }
        final Properties result = new Properties();
        if (propertySet != null) {
            result.putAll(propertySet.getProperties());
        }
        if (inlineProperties != null) {
            for (final Map.Entry<Object, Object> entry : inlineProperties.properties.entrySet()) {
                result.put(entry.getKey(), StringUtils.trim((String) entry.getValue()));
            }
        }
        return result;
    }

    private WeaverSettings getRef() {
        return getCheckedRef(WeaverSettings.class, "settings");
    }
}