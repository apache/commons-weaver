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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

/**
 * Represents a Java field.
 */
public class Field {
    public final int access;
    public final String name;
    public final Type type;

    public Field(int access, String name, Type type) {
        super();
        this.access = access;
        this.name = Validate.notNull(name);
        this.type = Validate.notNull(type);
    }

    /**
     * Considers name and type.
     * 
     * @param obj
     * @return whether equal
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Field == false) {
            return false;
        }
        final Field other = (Field) obj;
        return StringUtils.equals(other.name, name) && ObjectUtils.equals(other.type, type);
    }

    /**
     * Considers name and type.
     * 
     * @return hashCode
     */
    @Override
    public int hashCode() {
        int result = 57 << 2;
        result |= name.hashCode();
        result <<= 4;
        result |= type.hashCode();
        return result;
    }
}
