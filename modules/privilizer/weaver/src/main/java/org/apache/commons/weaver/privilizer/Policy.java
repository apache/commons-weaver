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
package org.apache.commons.weaver.privilizer;

import java.security.PrivilegedAction;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * Weaving policy: when to use {@link PrivilegedAction}s.
 */
public enum Policy {
    /**
     * Disables weaving.
     */
    NEVER,

    /**
     * Weaves such that the check for an active {@link SecurityManager} is done once only.
     */
    ON_INIT,

    /**
     * Weaves such that the check for an active {@link SecurityManager} is done for each {@link Privileged} method
     * execution.
     */
    DYNAMIC,

    /**
     * Weaves such that {@link Privileged} methods are always executed as such.
     */
    ALWAYS;

    /**
     * Gets the {@link Policy} value that should be used as a default.
     * @return {@link Policy#DYNAMIC}
     */
    public static Policy defaultValue() {
        return DYNAMIC;
    }

    /**
     * Parse from a {@link String} returning {@link #defaultValue()} for blank/null input.
     * @param str to parse
     * @return {@link Policy}
     */
    public static Policy parse(final String str) {
        if (StringUtils.isBlank(str)) {
            return defaultValue();
        }
        return valueOf(str.trim().toUpperCase(Locale.US));
    }

    /**
     * Learn whether this is a conditional {@link Policy}.
     * @return {@code this == ON_INIT || this == DYNAMIC}
     */
    public boolean isConditional() {
        return this == ON_INIT || this == DYNAMIC;
    }
}