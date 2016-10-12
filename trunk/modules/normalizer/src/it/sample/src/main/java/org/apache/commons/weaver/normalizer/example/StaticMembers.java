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

public final class StaticMembers {
    private StaticMembers() {
    }

    public static final TypeLiteral<String> STRING_TYPE = new TypeLiteral<String>() { };
    public static final TypeLiteral<String> STRING_TYPE2 = new TypeLiteral<String>() { };
    public static final TypeLiteral<Iterable<Integer>> INTEGER_ITERABLE_TYPE = new TypeLiteral<Iterable<Integer>>() { };

    public static final ContrivedWrapper WRAPPED_OBJECT = new ContrivedWrapper(new Object()) { };
    public static final ContrivedWrapper WRAPPED_STRING = new ContrivedWrapper("foo") { };
    public static final ContrivedWrapper WRAPPED_STRING2 = new ContrivedWrapper("foo") { };
    public static final ContrivedWrapper WRAPPED_INTEGER = new ContrivedWrapper(Integer.valueOf(1)) { };
    public static final ContrivedWrapper WRAPPED_INT = new ContrivedWrapper(1) { };
}
