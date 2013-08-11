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

import java.lang.reflect.Type;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * Same old "Type literal."
 */
public abstract class TypeLiteral<T> {
    public final Type value;

    protected TypeLiteral() {
        this.value =
            ObjectUtils
                .defaultIfNull(
                    TypeUtils.getTypeArguments(getClass(), TypeLiteral.class).get(
                        TypeLiteral.class.getTypeParameters()[0]), Object.class);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof TypeLiteral == false) {
            return false;
        }
        final TypeLiteral<?> other = (TypeLiteral<?>) obj;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return 37 << 4 | value.hashCode();
    }

}
