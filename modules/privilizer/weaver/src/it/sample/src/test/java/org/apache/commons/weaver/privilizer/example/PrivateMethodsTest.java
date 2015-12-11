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

import org.junit.Before;
import org.junit.Test;

public class PrivateMethodsTest {
    private PrivateMethods privateMethods;

    @Before
    public void setUp() throws Exception {
        Setup.setProperty("foo", "foo-value");
        Setup.setProperty("bar", "bar-value");
        Setup.setProperty("baz", "baz-value");
        privateMethods = new PrivateMethods();
    }

    @Test
    public void testGet() {
        assertEquals("foo-value", privateMethods.get("foo"));
        assertEquals("bar-value", privateMethods.get("bar"));
        assertEquals("baz-value", privateMethods.get("baz"));
    }
}
