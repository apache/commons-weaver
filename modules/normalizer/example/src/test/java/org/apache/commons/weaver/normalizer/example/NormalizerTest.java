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
package org.apache.commons.weaver.normalizer.example;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class NormalizerTest {
    private InstanceMembers instanceMembers;

    @Before
    public void setup() {
        instanceMembers = new InstanceMembers();
    }

    @Test
    public void testGenericType() {
        final Class<?> stringLiteral = StaticMembers.STRING_TYPE.getClass();
        assertTrue(stringLiteral.isInstance(StaticMembers.STRING_TYPE2));
        assertFalse(stringLiteral.isInstance(StaticMembers.INTEGER_ITERABLE_TYPE));
        assertTrue(stringLiteral.isInstance(instanceMembers.stringType));
        assertTrue(stringLiteral.isInstance(instanceMembers.stringType2));
        assertFalse(stringLiteral.isInstance(instanceMembers.integerIterableType));

        final Class<?> integerIterable = StaticMembers.INTEGER_ITERABLE_TYPE.getClass();
        assertTrue(integerIterable.isInstance(instanceMembers.integerIterableType));
    }

    @Test
    public void testAlternateConstructors() {
        final Class<?> objectWrapper = StaticMembers.WRAPPED_OBJECT.getClass();
        assertTrue(objectWrapper.isInstance(StaticMembers.WRAPPED_STRING));
        assertTrue(objectWrapper.isInstance(StaticMembers.WRAPPED_STRING2));
        assertTrue(objectWrapper.isInstance(StaticMembers.WRAPPED_INTEGER));
        assertFalse(objectWrapper.isInstance(StaticMembers.WRAPPED_INT));
        assertTrue(objectWrapper.isInstance(instanceMembers.wrappedObject));
        assertTrue(objectWrapper.isInstance(instanceMembers.wrappedString));
        assertTrue(objectWrapper.isInstance(instanceMembers.wrappedString2));
        assertTrue(objectWrapper.isInstance(instanceMembers.wrappedInteger));
        assertFalse(objectWrapper.isInstance(instanceMembers.wrappedInt));
        final Class<?> intWrapper = StaticMembers.WRAPPED_INT.getClass();
        assertTrue(intWrapper.isInstance(instanceMembers.wrappedInt));
    }
}
