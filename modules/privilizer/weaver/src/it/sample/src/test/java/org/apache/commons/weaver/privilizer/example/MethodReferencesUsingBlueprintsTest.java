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

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import org.junit.Before;
import org.junit.Test;

public class MethodReferencesUsingBlueprintsTest {

    private MethodReferencesUsingBlueprints methodReferencesUsingBlueprints;

    @Before
    public void setUp() throws Exception {
        Setup.setProperty("foo", "foo-value");
        Setup.setProperty("bar", "bar-value");
        Setup.setProperty("baz", "baz-value");
        methodReferencesUsingBlueprints = new MethodReferencesUsingBlueprints();
    }

    @Test
    public void testUtilsReadPublicConstant() {
        assertEquals(Utils.FOO, methodReferencesUsingBlueprints.utilsReadPublicConstant());
    }

    @Test
    public void testUtilsReadPrivateField() {
        assertEquals(999, methodReferencesUsingBlueprints.utilsReadPrivateField());
    }

    @Test
    public void testUtilsGetProperty() {
        assertEquals("foo-value", methodReferencesUsingBlueprints.utilsGetProperty());
    }

    @Test
    public void testUtilsGetProperty_String() {
        assertEquals("foo-value", methodReferencesUsingBlueprints.utilsGetProperty("foo"));
        assertEquals("bar-value", methodReferencesUsingBlueprints.utilsGetProperty("bar"));
        assertEquals("baz-value", methodReferencesUsingBlueprints.utilsGetProperty("baz"));
    }

    @Test
    public void testUtilsGetProperty_int_String() {
        assertEquals("foo-value", methodReferencesUsingBlueprints.utilsGetProperty(2, "foo"));
        assertEquals("bar-value", methodReferencesUsingBlueprints.utilsGetProperty(2, "bar"));
        assertEquals("baz-value", methodReferencesUsingBlueprints.utilsGetProperty(2, "baz"));
    }

    @Test
    public void testMoreGetProperty() {
        assertEquals("bar-value", methodReferencesUsingBlueprints.moreGetProperty());
    }

    @Test
    public void testMoreGetTopStackElementClassName() {
        assumeTrue(StringUtils.containsIgnoreCase(SystemUtils.JAVA_VENDOR, "oracle"));
        assertEquals(Utils.More.class.getName(), methodReferencesUsingBlueprints.moreGetTopStackElementClassName());
    }
}
