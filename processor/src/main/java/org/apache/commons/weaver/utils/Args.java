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
package org.apache.commons.weaver.utils;

/**
 * Argument/Parameter-related utilities.
 */
public final class Args {

    private Args() {
    }

    /**
     * Compare two parameter type arrays.
     *
     * @param paramTypes1
     *            lhs
     * @param paramTypes2
     *            rhs
     * @return {@code int} as specified by {@link java.util.Comparator#compare(Object, Object)}
     */
    @SuppressWarnings("PMD.UseVarargs") // not needed for comparing one array to another
    public static int compare(final Class<?>[] paramTypes1, final Class<?>[] paramTypes2) {
        for (int param = 0; param < paramTypes1.length; param++) {
            if (param >= paramTypes2.length) {
                return 1;
            }
            final int test = paramTypes1[param].getName().compareTo(paramTypes2[param].getName());
            if (test == 0) {
                continue;
            }
            return test;
        }
        if (paramTypes1.length == paramTypes2.length) {
            return 0;
        }
        return -1;
    }
}
