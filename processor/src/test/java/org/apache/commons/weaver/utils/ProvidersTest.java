/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.weaver.utils;

import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.apache.commons.weaver.Consumes;
import org.apache.commons.weaver.Produces;
import org.apache.commons.weaver.lifecycle.WeaveLifecycleToken;
import org.apache.commons.weaver.spi.WeaveLifecycleProvider;
import org.apache.commons.weaver.spi.Weaver;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Test;
import org.junit.jupiter.api.function.Executable;

public class ProvidersTest {

    @Test
    public void testSortNull() {
        assertThrows(NullPointerException.class, () -> Providers.sort(null));
    }

    @Test
    public void testSortNullElement() {
        assertThrows(IllegalArgumentException.class, () -> Providers.sort(Arrays.asList((Weaver) null)));
    }

    public interface FauxWeaveProvider extends WeaveLifecycleProvider<WeaveLifecycleToken.Weave> {
    }

    public class A implements FauxWeaveProvider {
    }

    @Consumes(A.class)
    @Produces(C.class)
    public class B implements FauxWeaveProvider {
    }

    public class C implements FauxWeaveProvider {
    }

    @Consumes(X.class)
    public class W implements FauxWeaveProvider {
    }

    @Consumes(Y.class)
    public class X implements FauxWeaveProvider {
    }

    public class Y implements FauxWeaveProvider {
    }

    @Produces(Y.class)
    public class Z implements FauxWeaveProvider {
    }

    @Consumes(Y.class)
    @Produces(Z.class)
    public class Monkeywrench implements FauxWeaveProvider {
    }

    private final FauxWeaveProvider a = new A(), b = new B(), c = new C(), w = new W(), x = new X(), y = new Y(), z = new Z(),
                    monkeywrench = new Monkeywrench();

    @Test
    public void testSort() {
        assertThat(Providers.sort(Arrays.asList(b, a, c)), IsIterableContainingInOrder.contains(a, b, c));
        assertThat(Providers.sort(Arrays.asList(y, w, x, z)), IsIterableContainingInOrder.contains(z, y, x, w));
    }

    @Test
    public void testCircularSort() {
        assertThrows(IllegalStateException.class, () -> Providers.sort(Arrays.asList(y, z, monkeywrench)));
    }

}
