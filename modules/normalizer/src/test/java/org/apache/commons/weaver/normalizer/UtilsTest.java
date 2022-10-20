/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.weaver.normalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;

public class UtilsTest {

    @Test
    public void testValidatePackageName() {
        assertEquals("", Utils.validatePackageName(""));
        assertEquals("", Utils.validatePackageName("    "));
        assertEquals("foo", Utils.validatePackageName("foo"));
        assertEquals("foo/bar", Utils.validatePackageName("foo.bar"));
        assertEquals("foo/bar", Utils.validatePackageName("foo/bar"));
        assertEquals("foo/bar/baz", Utils.validatePackageName("foo.bar.baz"));
        assertEquals("foo/bar/baz", Utils.validatePackageName("foo.bar/baz"));
        assertEquals("foo/bar/baz", Utils.validatePackageName("foo/bar.baz"));
        assertEquals("foo/bar/baz", Utils.validatePackageName("foo/bar/baz"));
        assertEquals("$foo", Utils.validatePackageName("$foo"));
        assertEquals("_foo", Utils.validatePackageName("_foo"));
        assertEquals("foo2", Utils.validatePackageName("foo2"));
    }

    @Test
    public void testValidatePackageNameStartsWithDigit() {
        assertThrows(IllegalArgumentException.class, () -> Utils.validatePackageName("2foo"));
    }

    @Test
    public void testValidatePackageNameEmbeddedWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> Utils.validatePackageName("foo bar"));
    }

    @Test
    public void testParseTypes() {
        assertContainsInOrder(Utils.parseTypes(" java.lang.Number ", getClass().getClassLoader()), Number.class);
        assertContainsInOrder(
            Utils.parseTypes("java.lang.Number,java.lang.String,java.util.Map", getClass().getClassLoader()),
            Number.class, String.class, Map.class);
        assertContainsInOrder(
            Utils.parseTypes("java.lang.Number, java.lang.String, java.util.Map", getClass().getClassLoader()),
            Number.class, String.class, Map.class);
        assertContainsInOrder(
            Utils.parseTypes("java/lang/Number, java/lang/String, java/util/Map", getClass().getClassLoader()),
            Number.class, String.class, Map.class);
        assertContainsInOrder(
            Utils.parseTypes("java.lang.Number,\njava.lang.String,\njava.util.Map", getClass().getClassLoader()),
            Number.class, String.class, Map.class);
    }

    @Test
    public void testParseUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> Utils.parseTypes("gobbledygook", getClass().getClassLoader()));
    }

    @Test
    public void testParseMissingFirstType() {
        assertThrows(IllegalArgumentException.class, () -> Utils.parseTypes(",java.lang.Object", getClass().getClassLoader()));
    }

    @Test
    public void testParseMissingLastType() {
        assertThrows(IllegalArgumentException.class, () -> Utils.parseTypes("java.lang.Object,", getClass().getClassLoader()));
    }

    @Test
    public void testParseMissingType() {
        assertThrows(IllegalArgumentException.class, () -> Utils.parseTypes("java.lang.Object,,java.lang.Iterable", getClass().getClassLoader()));
    }

    <E> void assertContainsInOrder(final Iterable<E> iterable, final E... expectedElements) {
        final Iterator<E> iterator = iterable.iterator();
        for (final E e : expectedElements) {
            assertTrue(iterator.hasNext());
            assertEquals(e, iterator.next());
        }
        assertFalse(iterator.hasNext());
    }

}
