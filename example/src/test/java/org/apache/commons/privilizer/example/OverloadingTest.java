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
package org.apache.commons.privilizer.example;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.commons.privilizer.example.Overloading;

import junit.framework.TestCase;

public class OverloadingTest extends TestCase {
    private Overloading overloading;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                System.setProperty("foo", "foo-value");
                System.setProperty("bar", "bar-value");
                System.setProperty("baz", "baz-value");
                return null;
            }
        });
        overloading = new Overloading();
    }

    public void testNoArgs() {
        assertEquals("foo-value", overloading.get());
    }

    public void testStringArg() {
        assertEquals("bar-value", overloading.get("bar"));
    }

    public void testCharishArgs() {
        assertEquals("baz-value", overloading.get('b', 'a', (short) 'z'));
    }
}
