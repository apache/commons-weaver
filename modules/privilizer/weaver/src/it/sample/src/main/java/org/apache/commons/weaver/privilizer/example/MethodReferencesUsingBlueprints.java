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

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.apache.commons.weaver.privilizer.Privilizing;
import org.apache.commons.weaver.privilizer.Privilizing.CallTo;

@Privilizing({ @CallTo(Utils.class), @CallTo(value = Utils.More.class, methods = "getProperty") })
public class MethodReferencesUsingBlueprints {

    public String utilsReadPublicConstant() {
        final Supplier<String> s = Utils::readPublicConstant;
        return s.get();
    }

    public int utilsReadPrivateField() {
        final IntSupplier s = Utils::readPrivateField;
        return s.getAsInt();
    }

    public String utilsGetProperty() {
        final Supplier<String> s = Utils::getProperty;
        return s.get();
    }

    public String utilsGetProperty(int i, String key) {
        final BiFunction<Integer, String, String> f = Utils::getProperty;
        return f.apply(i, key);
    }

    public String utilsGetProperty(String key) {
        final UnaryOperator<String> o = Utils::getProperty;
        return o.apply(key);
    }

    public String moreGetProperty() {
        final Supplier<String> s = Utils.More::getProperty;
        return s.get();
    }

    public String moreGetTopStackElementClassName() {
        final Supplier<String> s = Utils.More::getTopStackElementClassName;
        return s.get();
    }
}
