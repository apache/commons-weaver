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

import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

public enum AccessLevel {
    PUBLIC(Modifier.PUBLIC), PROTECTED(Modifier.PROTECTED), PACKAGE(0), PRIVATE(Modifier.PRIVATE);

    private final int flag;

    private AccessLevel(int flag) {
        this.flag = flag;
    }

    public static AccessLevel of(int mod) {
        final Set<AccessLevel> matched = EnumSet.noneOf(AccessLevel.class);
        if (Modifier.isPublic(mod)) {
            matched.add(PUBLIC);
        }
        if (Modifier.isProtected(mod)) {
            matched.add(PROTECTED);
        }
        if (Modifier.isPrivate(mod)) {
            matched.add(PRIVATE);
        }
        if (matched.isEmpty()) {
            return PACKAGE;
        }
        Validate.isTrue(matched.size() == 1, "%s seems to declare multiple access modifiers: %s", mod, matched);
        return matched.iterator().next();
    }

    public int merge(int mod) {
        int remove = 0;
        for (AccessLevel accessLevel : EnumSet.complementOf(EnumSet.of(this))) {
            remove |= accessLevel.flag;
        }
        return mod & ~remove | flag;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.US);
    }

    /**
     * Get the {@link AccessLevel} value that should be used as a default.
     * 
     * @return {@link AccessLevel#PRIVATE}
     */
    public static AccessLevel defaultValue() {
        return AccessLevel.PRIVATE;
    }

    /**
     * Parse from a {@link String} returning {@link #defaultValue()} for blank/null input.
     * 
     * @param s
     * @return {@link AccessLevel}
     */
    public static AccessLevel parse(String s) {
        if (StringUtils.isBlank(s)) {
            return defaultValue();
        }
        return valueOf(s.trim().toUpperCase(Locale.US));
    }
}
