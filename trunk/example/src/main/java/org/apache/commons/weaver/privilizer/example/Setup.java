/*
 *  Copyright the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.commons.weaver.privilizer.example;

import java.security.AccessController;
import java.security.PrivilegedAction;

final class Setup {
    private Setup() {
    }

    /**
     * This simply allows us to to set up test classes by doing
     * privileged things without granting privileges to the test
     * code itself and thus making it impossible to test the effects
     * of privilization.
     */
    public static void setProperty(final String name, final String value) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.setProperty(name, value);
                return null;
            }
        });
    }
}
