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
package org.apache.commons.weaver.privilizer;

public class AlreadyWovenException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "%s already woven with policy %s";

    private final String classname;
    private final Privilizer.Policy policy;

    public AlreadyWovenException(String classname, Privilizer.Policy policy) {
        super(String.format(MESSAGE, classname, policy));
        this.classname = classname;
        this.policy = policy;
    }

    public String getClassname() {
        return classname;
    }

    public Privilizer.Policy getPolicy() {
        return policy;
    }

}
