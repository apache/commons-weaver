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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.apache.commons.weaver.privilizer.example.StaticNoArgs.CheckedException1;
import org.apache.commons.weaver.privilizer.example.StaticNoArgs.CheckedException2;
import org.junit.Before;
import org.junit.Test;

public class StaticNoArgsTest {
    @Before
    public void setUp() throws Exception {
        Setup.setProperty("foo", "foo-value");
    }

    @Test
    public void testThrowAwayFoo() {
        StaticNoArgs.throwAwayFoo();
    }

    @Test
    public void testGetFoo() {
        assertEquals("foo-value", StaticNoArgs.getFoo());
    }

    @Test
    public void testGetTrue() {
        assertSame(Boolean.TRUE, StaticNoArgs.getTrue());
    }

    @Test
    public void testGetFalse() {
        assertFalse(StaticNoArgs.getFalse());
    }

    @Test
    public void testThrowingCheckedException1() {
        try {
            StaticNoArgs.throwingCheckedException1();
            fail();
        } catch (CheckedException1 e) {
        }
    }

    @Test
    public void testThrowingCheckedException2() {
        try {
            StaticNoArgs.throwingCheckedException2();
        } catch (CheckedException1 e) {
        } catch (CheckedException2 e) {
            return;
        }
        fail();
    }
}
