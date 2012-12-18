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
package org.apache.commons.weaver.utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * {@link URL} Array utilities.
 */
public abstract class URLArray {
    private URLArray() {
    }

    /**
     * Convert an {@link Iterable} of filesystem paths.
     * 
     * @param files
     * @return URL[]
     */
    public static URL[] fromPaths(final Iterable<String> files) {
        return fromFiles(new Iterable<File>() {

            public Iterator<File> iterator() {
                final Iterator<String> path = files.iterator();
                return new Iterator<File>() {

                    public boolean hasNext() {
                        return path.hasNext();
                    }

                    public File next() {
                        final String p = path.next();
                        return p == null ? null : new File(p);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        });
    }

    /**
     * Convert an {@link Iterable} of {@link File}s.
     * 
     * @param files
     * @return URL[]
     */
    public static URL[] fromFiles(Iterable<File> files) {
        final ArrayList<URL> result = new ArrayList<URL>();
        for (File f : files) {
            if (f == null) {
                result.add(null);
                continue;
            }
            try {
                result.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
                // this shouldn't happen
                throw new RuntimeException(e);
            }
        }
        return result.toArray(new URL[result.size()]);
    }
}
