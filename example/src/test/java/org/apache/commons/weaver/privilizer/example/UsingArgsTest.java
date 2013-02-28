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
package org.apache.commons.weaver.privilizer.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

import org.apache.commons.weaver.privilizer.example.UsingArgs.CheckedException1;
import org.apache.commons.weaver.privilizer.example.UsingArgs.CheckedException2;

public class UsingArgsTest {
    private UsingArgs usingArgs;

    public void setUp() throws Exception {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                System.setProperty("foo", "foo-value");
                System.setProperty("bar", "bar-value");
                System.setProperty("baz", "baz-value");
                return null;
            }
        });
        usingArgs = new UsingArgs();
    }

    public void testGetProperty() {
        assertEquals("foo-value", usingArgs.getProperty("foo"));
        assertEquals("bar-value", usingArgs.getProperty("bar"));
        assertEquals("baz-value", usingArgs.getProperty("baz"));
    }

    public void testGetProperties() {
        assertTrue(Arrays.equals(new String[] { "foo-value", "bar-value", "baz-value" },
            usingArgs.getProperties("foo", "bar", "baz")));
        assertEquals(0, usingArgs.getProperties().length);
        assertNull(usingArgs.getProperties((String[]) null));
    }

    public void testThrowAwayProperty() {
        usingArgs.throwAwayProperty('f', "o", 'o');
    }

    public void testAssembleAndGetProperty() {
        assertEquals("foo-value", usingArgs.assembleAndGetProperty('f', new StringBuilder().append('o'), 'o'));
        assertEquals("bar-value", usingArgs.assembleAndGetProperty('b', new StringBuilder().append('a'), 'r'));
        assertEquals("baz-value", usingArgs.assembleAndGetProperty('b', new StringBuilder().append('a'), 'z'));
    }

    public void testThrowingCheckedException() throws CheckedException1, CheckedException2 {
        assertEquals(0, usingArgs.throwingCheckedException(0, "foo"));
        try {
            usingArgs.throwingCheckedException(1, "bar");
        } catch (CheckedException1 e) {
        }
        try {
            usingArgs.throwingCheckedException(2, "baz");
        } catch (CheckedException2 e) {
        }
    }
}
