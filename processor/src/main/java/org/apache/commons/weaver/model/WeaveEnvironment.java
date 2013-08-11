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
package org.apache.commons.weaver.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Logger;

import javax.activation.DataSource;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.spi.Cleaner;
import org.apache.commons.weaver.spi.Weaver;

/**
 * Encapsulates the environment in which a {@link Weaver} or {@link Cleaner} must operate.
 */
public abstract class WeaveEnvironment {
    private static final String CONTENT_TYPE = "application/octet-stream";

    private class Resource implements DataSource {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public String getContentType() {
            return CONTENT_TYPE;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return classLoader.getResourceAsStream(name);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return WeaveEnvironment.this.getOutputStream(name);
        }

    }

    /**
     * ClassLoader containing scannable and weavable classes.
     */
    public final ClassLoader classLoader;

    /**
     * Configuration properties. By convention, any configuration property should start with its name, e.g.
     * "privilizer".
     */
    public final Properties config;

    private final Logger log;

    /**
     * Create a new {@link WeaveEnvironment}.
     * 
     * @param classLoader
     * @param config
     * @param log
     */
    protected WeaveEnvironment(ClassLoader classLoader, Properties config, Logger log) {
        super();
        this.classLoader = classLoader;
        this.config = (Properties) Validate.notNull(config, "config").clone();
        this.log = log;
    }

    public void debug(String message, Object... args) {
        log.fine(String.format(message, args));
    }

    public void verbose(String message, Object... args) {
        log.fine(String.format(message, args));
    }

    public void warn(String message, Object... args) {
        log.warning(String.format(message, args));
    }

    public void info(String message, Object... args) {
        log.info(String.format(message, args));
    }

    public void error(String message, Object... args) {
        log.severe(String.format(message, args));
    }

    public final DataSource getClassfile(Class<?> cls) {
        return getClassfile(cls.getName());
    }

    public final DataSource getClassfile(String classname) {
        return getResource(getResourceName(classname));
    }

    public final DataSource getResource(String name) {
        return new Resource(name);
    }

    public final boolean deleteClassfile(Class<?> cls) {
        return deleteClassfile(cls.getName());
    }

    public final boolean deleteClassfile(String classname) {
        return deleteResource(getResourceName(classname));
    }

    public abstract boolean deleteResource(String name);

    protected abstract OutputStream getOutputStream(String resourceName) throws IOException;

    protected static String getResourceName(String classname) {
        return classname.replace('.', '/') + ".class";
    }
}
