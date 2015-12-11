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

import java.util.ArrayList;

import org.apache.commons.weaver.privilizer.Privileged;

public class UsingArgs {

    @Privileged
    String getProperty(String name) {
        return System.getProperty(name);
    }

    @Privileged
    String[] getProperties(String... names) {
        if (names == null) {
            return null;
        }
        final ArrayList<String> result = new ArrayList<String>();
        // in reality one would delegate to #getProperty to minimize the scope
        // of the privileged action
        for (String name : names) {
            result.add(System.getProperty(name));
        }
        return result.toArray(new String[result.size()]);
    }

    @Privileged
    void throwAwayProperty(int first, String middle, char last) {
        System.getProperty(new StringBuilder().append((char) first).append(middle).append(last).toString());
    }

    @Privileged
    Object assembleAndGetProperty(char first, CharSequence middle, int last) {
        return System.getProperty(new StringBuilder().append(first).append(middle).append((char) last).toString());
    }

    public static class CheckedException1 extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static class CheckedException2 extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Privileged
    int throwingCheckedException(int which, String propertyToGet) throws CheckedException1, CheckedException2 {
        System.getProperty(propertyToGet);
        switch (which) {
        case 1:
            throw new CheckedException1();
        case 2:
            throw new CheckedException2();
        default:
            return which;
        }
    }
}
