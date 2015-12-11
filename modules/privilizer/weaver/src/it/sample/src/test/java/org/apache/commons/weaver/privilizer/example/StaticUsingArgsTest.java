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
package org.apache.commons.weaver.privilizer.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.apache.commons.weaver.privilizer.example.StaticUsingArgs.CheckedException1;
import org.apache.commons.weaver.privilizer.example.StaticUsingArgs.CheckedException2;
import org.junit.Before;
import org.junit.Test;

public class StaticUsingArgsTest {

    @Before
    public void setUp() throws Exception {
        Setup.setProperty("foo", "foo-value");
        Setup.setProperty("bar", "bar-value");
        Setup.setProperty("baz", "baz-value");
    }

    @Test
    public void testGetProperty() {
        assertEquals("foo-value", StaticUsingArgs.getProperty("foo"));
        assertEquals("bar-value", StaticUsingArgs.getProperty("bar"));
        assertEquals("baz-value", StaticUsingArgs.getProperty("baz"));
    }

    @Test
    public void testGetProperties() {
        assertTrue(Arrays.equals(new String[] { "foo-value", "bar-value", "baz-value" },
            StaticUsingArgs.getProperties("foo", "bar", "baz")));
        assertEquals(0, StaticUsingArgs.getProperties().length);
        assertNull(StaticUsingArgs.getProperties((String[]) null));
    }

    @Test
    public void testThrowAwayProperty() {
        StaticUsingArgs.throwAwayProperty('f', "o", 'o');
    }

    @Test
    public void testAssembleAndGetProperty() {
        assertEquals("foo-value", StaticUsingArgs.assembleAndGetProperty('f', new StringBuilder().append('o'), 'o'));
        assertEquals("bar-value", StaticUsingArgs.assembleAndGetProperty('b', new StringBuilder().append('a'), 'r'));
        assertEquals("baz-value", StaticUsingArgs.assembleAndGetProperty('b', new StringBuilder().append('a'), 'z'));
    }

    @Test
    public void testThrowingCheckedException() throws CheckedException1, CheckedException2 {
        assertEquals(0, StaticUsingArgs.throwingCheckedException(0, "foo"));
        try {
            StaticUsingArgs.throwingCheckedException(1, "bar");
        } catch (CheckedException1 e) {
        }
        try {
            StaticUsingArgs.throwingCheckedException(2, "baz");
        } catch (CheckedException2 e) {
        }
    }
}
