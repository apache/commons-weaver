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

import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.Locale;

public enum AccessLevel {
    PUBLIC(Modifier.PUBLIC), PROTECTED(Modifier.PROTECTED), PACKAGE(0), PRIVATE(Modifier.PRIVATE);

    private final int flag;

    private AccessLevel(int flag) {
        this.flag = flag;
    }

    public static AccessLevel of(int mod) {
        if (Modifier.isPublic(mod)) {
            return PUBLIC;
        }
        if (Modifier.isProtected(mod)) {
            return PROTECTED;
        }
        if (Modifier.isPrivate(mod)) {
            return PRIVATE;
        }
        return PACKAGE;
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
}
