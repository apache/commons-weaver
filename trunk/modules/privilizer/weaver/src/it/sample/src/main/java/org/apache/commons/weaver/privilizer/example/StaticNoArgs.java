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

import org.apache.commons.weaver.privilizer.Privileged;

public abstract class StaticNoArgs {
    private StaticNoArgs() {
    }

    @Privileged
    static void throwAwayFoo() {
        System.getProperty("foo");
    }

    @Privileged
    static String getFoo() {
        return System.getProperty("foo");
    }

    @Privileged
    static Boolean getTrue() {
        System.getProperty("foo");
        return Boolean.TRUE;
    }

    @Privileged
    static boolean getFalse() {
        System.getProperty("foo");
        return false;
    }

    public static class CheckedException1 extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static class CheckedException2 extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Privileged
    static void throwingCheckedException1() throws CheckedException1 {
        System.getProperty("foo");
        throw new CheckedException1();
    }

    @Privileged
    static Integer throwingCheckedException2() throws CheckedException1, CheckedException2 {
        System.getProperty("foo");
        throw new CheckedException2();
    }
}
