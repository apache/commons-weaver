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
import java.util.Collection;
import java.util.stream.Stream;

/**
 * {@link URL} Array utilities.
 */
public final class URLArray {
    /**
     * Convert an {@link Iterable} of filesystem paths.
     * @param files to convert
     * @return URL[]
     */
    public static URL[] fromPaths(final Iterable<String> files) {
        return fromFiles(() -> stream(files).map(e -> e == null ? null : new File(e)).iterator());
    }

    /**
     * Convert an {@link Iterable} of {@link File}s.
     * @param files to convert
     * @return URL[]
     */
    public static URL[] fromFiles(final Iterable<File> files) {
        return fromFiles(stream(files));
    }

    private static URL[] fromFiles(final Stream<File> files) {
        return files.map(f -> {
            if (f == null) {
                return null;
            }
            try {
                return f.toURI().toURL();
            } catch (final MalformedURLException e) {
                // this shouldn't happen
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);
    }

    private static <T> Stream<T> stream(final Iterable<T> iterable) {
        if (iterable instanceof Collection<?>) {
            return ((Collection<T>) iterable).stream();
        }
        final Stream.Builder<T> builder = Stream.builder();
        iterable.forEach(builder);
        return builder.build();
    }

    private URLArray() {
    }
}
