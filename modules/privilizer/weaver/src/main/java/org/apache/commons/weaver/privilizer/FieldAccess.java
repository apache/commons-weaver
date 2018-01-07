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

import org.objectweb.asm.Type;

/**
 * Models the action of accessing a field by extending {@link Field} with an
 * accessing type.
 */
public class FieldAccess extends Field {
    /**
     * {@link Type} from which field is accessed.
     */
    public final Type owner;

    /**
     * Create a new {@link FieldAccess}.
     * @param access operation
     * @param owner {@link Type} from which field is accessed.
     * @param name of field
     * @param type of field
     */
    public FieldAccess(final int access, final Type owner, final String name, final Type type) {
        super(access, name, type);
        this.owner = owner;
    }

    /**
     * Compare against {@code obj} for equality.
     * @param obj to compare
     * @return whether Objects are equal
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof FieldAccess)) {
            return false;
        }
        return super.equals(obj) && ((FieldAccess) obj).owner.equals(owner);
    }

    /**
     * Generate a hashCode.
     * @return int
     */
    @Override
    public int hashCode() {
        final int result = super.hashCode() << 4;
        return result | owner.hashCode();
    }
}
