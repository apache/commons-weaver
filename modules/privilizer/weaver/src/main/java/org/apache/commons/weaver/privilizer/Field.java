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
package org.apache.commons.weaver.privilizer;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

/**
 * Represents a Java field.
 */
public class Field {
    /**
     * Access modifier.
     */
    public final int access;

    /**
     * Field name.
     */
    public final String name;

    /**
     * Field type.
     */
    public final Type type;

    /**
     * Create a new {@link Field}.
     * @param access modifier
     * @param name of field
     * @param type of field
     */
    public Field(final int access, final String name, final Type type) {
        super();
        this.access = access;
        this.name = Validate.notNull(name);
        this.type = Validate.notNull(type);
    }

    /**
     * Considers name and type.
     * @param obj to check for equality
     * @return whether equal
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Field)) {
            return false;
        }
        final Field other = (Field) obj;
        return StringUtils.equals(other.name, name) && Objects.equals(other.type, type);
    }

    /**
     * Considers name and type.
     * @return hashCode
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
}
