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

import org.apache.commons.weaver.privilizer.Privilizing;
import org.apache.commons.weaver.privilizer.Privilizing.CallTo;

@Privilizing({ @CallTo(Utils.class), @CallTo(value = Utils.More.class, methods = "getProperty") })
public class UsingBlueprints {

    public String utilsGetProperty() {
        return Utils.getProperty();
    }

    public String utilsGetProperty(int i, String key) {
        return Utils.getProperty(i, key);
    }

    public String utilsGetProperty(String key) {
        return Utils.getProperty(key);
    }

    public String moreGetProperty() {
        return Utils.More.getProperty();
    }

    public String moreGetTopStackElementClassName() {
        return Utils.More.getTopStackElementClassName();
    }

    private void foo() {
    }
}
