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
package org.apache.commons.weaver.utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * {@link URL} Array utilities.
 */
public final class URLArray {
    private URLArray() {
    }

    /**
     * Convert an {@link Iterable} of filesystem paths.
     * @param files to convert
     * @return URL[]
     */
    public static URL[] fromPaths(final Iterable<String> files) {
        return fromFiles(new Iterable<File>() {

            @Override
            public Iterator<File> iterator() {
                final Iterator<String> path = files.iterator();
                return new Iterator<File>() {

                    @Override
                    public boolean hasNext() {
                        return path.hasNext();
                    }

                    @Override
                    public File next() {
                        final String element = path.next();
                        return element == null ? null : new File(element);
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        });
    }

    /**
     * Convert an {@link Iterable} of {@link File}s.
     * @param files to convert
     * @return URL[]
     */
    public static URL[] fromFiles(final Iterable<File> files) {
        final ArrayList<URL> result = new ArrayList<URL>();
        for (final File file : files) {
            if (file == null) {
                result.add(null);
                continue;
            }
            try {
                result.add(file.toURI().toURL());
            } catch (final MalformedURLException e) {
                // this shouldn't happen
                throw new RuntimeException(e);
            }
        }
        return result.toArray(new URL[result.size()]);
    }
}
