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

import static org.junit.Assert.*;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

/**
 * {@link Args} tests.
 */
public class ArgsTest {

    @Test
    public void testCompare() {
        assertTrue(Args.compare(ArrayUtils.EMPTY_CLASS_ARRAY, ArrayUtils.EMPTY_CLASS_ARRAY) == 0);
        assertTrue(Args.compare(ArrayUtils.EMPTY_CLASS_ARRAY, new Class[] { String.class }) < 0);
        assertTrue(Args.compare(new Class[] { String.class }, ArrayUtils.EMPTY_CLASS_ARRAY) > 0);
        assertTrue(Args.compare(new Class[] { String.class }, new Class[] { String.class }) == 0);
        assertTrue(Args.compare(new Class[] { int.class }, new Class[] { String.class }) < 0);
        assertTrue(Args.compare(new Class[] { String.class }, new Class[] { int.class }) > 0);
        assertTrue(Args.compare(new Class[] { int.class, String.class }, new Class[] { int.class, String.class}) == 0);
        assertTrue(Args.compare(new Class[] { String.class, String.class }, new Class[] { String.class, String.class}) == 0);
        assertTrue(Args.compare(new Class[] { String.class, int.class }, new Class[] { String.class, String.class}) < 0);
        assertTrue(Args.compare(new Class[] { String.class, String.class }, new Class[] { String.class, int.class}) > 0);
    }

}
