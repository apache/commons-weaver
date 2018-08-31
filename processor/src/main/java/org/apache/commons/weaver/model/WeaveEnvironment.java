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
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.spi.Cleaner;
import org.apache.commons.weaver.spi.Weaver;

/**
 * Encapsulates the environment in which a {@link Weaver} or {@link Cleaner} must operate.
 */
public abstract class WeaveEnvironment {
    /**
     * Represents a {@link WeaveEnvironment} resource.
     */
    public class Resource {
        private final String name;

        Resource(final String name) {
            this.name = name;
        }

        /**
         * Get the content type, always "application/octet-stream".
         * @return {@link String}
         */
        public String getContentType() {
            return CONTENT_TYPE;
        }

        /**
         * Get an {@link InputStream} for reading this {@link Resource}.
         * @return {@link InputStream}
         * @throws IOException if unable to read
         */
        public InputStream getInputStream() throws IOException {
            return classLoader.getResourceAsStream(name);
        }

        /**
         * Get the name of this {@link Resource}.
         * @return {@link String}
         */
        public String getName() {
            return name;
        }

        /**
         * Get an {@link OutputStream} for writing to this {@link Resource}.
         * @return {@link OutputStream}
         * @throws IOException if unable to write
         */
        public OutputStream getOutputStream() throws IOException {
            return WeaveEnvironment.this.getOutputStream(name);
        }
    }

    /**
     * Content type for environment resource.
     */
    static final String CONTENT_TYPE = "application/octet-stream";

    /**
     * Convert a classname into a resource name.
     * @param classname to convert
     * @return String
     */
    protected static String getResourceName(final String classname) {
        return classname.replace('.', '/') + ".class";
    }

    private static Supplier<String> supplier(final String format, final Object... args) {
        return () -> String.format(format, args);
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
     * @param classLoader property
     * @param config property
     * @param log property
     */
    protected WeaveEnvironment(final ClassLoader classLoader, final Properties config, final Logger log) {
        super();
        this.classLoader = classLoader;
        this.config = (Properties) Validate.notNull(config, "config").clone();
        this.log = log;
    }

    /**
     * Handle a debug message.
     * @param message text
     * @param args format
     * @see String#format(String, Object...)
     */
    public void debug(final String message, final Object... args) {
        log.fine(supplier(message, args));
    }

    /**
     * Handle a verbose message.
     * @param message text
     * @param args format
     * @see String#format(String, Object...)
     */
    public void verbose(final String message, final Object... args) {
        log.fine(supplier(message, args));
    }

    /**
     * Handle a warning message.
     * @param message text
     * @param args format
     * @see String#format(String, Object...)
     */
    public void warn(final String message, final Object... args) {
        log.warning(supplier(message, args));
    }

    /**
     * Handle an info message.
     * @param message text
     * @param args format
     * @see String#format(String, Object...)
     */
    public void info(final String message, final Object... args) {
        log.info(supplier(message, args));
    }

    /**
     * Handle an error message.
     * @param message text
     * @param args format
     * @see String#format(String, Object...)
     */
    public void error(final String message, final Object... args) {
        log.severe(supplier(message, args));
    }

    /**
     * Get a {@link Resource} representing {@code cls}.
     * @param cls type
     * @return {@link Resource}
     */
    public final Resource getClassfile(final Class<?> cls) {
        return getClassfile(cls.getName());
    }

    /**
     * Get a {@link Resource} for the specified class.
     * @param classname of type
     * @return {@link Resource}
     */
    public final Resource getClassfile(final String classname) {
        return getResource(getResourceName(classname));
    }

    /**
     * Get a {@link Resource} for the specified resource.
     * @param name of resource
     * @return {@link Resource}
     */
    public final Resource getResource(final String name) {
        return new Resource(name);
    }

    /**
     * Delete the classfile for {@code cls}.
     * @param cls type
     * @return whether successful
     */
    public final boolean deleteClassfile(final Class<?> cls) {
        return deleteClassfile(cls.getName());
    }

    /**
     * Delete the classfile for the specified class.
     * @param classname of type
     * @return whether successful
     */
    public final boolean deleteClassfile(final String classname) {
        return deleteResource(getResourceName(classname));
    }

    /**
     * Delete the specified resource.
     * @param name to delete
     * @return whether successful
     */
    public abstract boolean deleteResource(String name);

    /**
     * Open an {@link OutputStream} for the specified resource.
     * @param resourceName to open
     * @return {@link OutputStream}
     * @throws IOException on error
     */
    protected abstract OutputStream getOutputStream(String resourceName) throws IOException;
}
