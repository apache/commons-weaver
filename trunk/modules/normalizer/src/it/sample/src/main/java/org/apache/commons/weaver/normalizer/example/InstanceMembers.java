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

import org.apache.commons.lang3.reflect.TypeLiteral;

public class InstanceMembers {
    public final TypeLiteral<String> stringType = new TypeLiteral<String>() { };
    public final TypeLiteral<String> stringType2 = new TypeLiteral<String>() { };
    public final TypeLiteral<Iterable<Integer>> integerIterableType = new TypeLiteral<Iterable<Integer>>() { };

    public final ContrivedWrapper wrappedObject = new ContrivedWrapper(new Object()) { };
    public final ContrivedWrapper wrappedString = new ContrivedWrapper("foo") { };
    public final ContrivedWrapper wrappedString2 = new ContrivedWrapper("foo") { };
    public final ContrivedWrapper wrappedInteger = new ContrivedWrapper(Integer.valueOf(1)) { };
    public final ContrivedWrapper wrappedInt = new ContrivedWrapper(1) { };
}
