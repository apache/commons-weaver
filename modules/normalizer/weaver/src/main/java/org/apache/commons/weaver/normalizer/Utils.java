/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.weaver.normalizer;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.Conversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

class Utils {

    static String validatePackageName(String pkg) {
        if (StringUtils.isBlank(pkg)) {
            return "";
        }
        pkg = pkg.trim();

        final String unexpected = "Unexpected character %s at pos %s of package name \"%s\"";

        boolean next = true;
        for (int pos = 0; pos < pkg.length(); pos++) {
            final char c = pkg.charAt(pos);
            if (next) {
                next = false;
                Validate.isTrue(Character.isJavaIdentifierStart(c), unexpected, c, pos, pkg);
                continue;
            }
            if (c == '/' || c == '.') {
                next = true;
                continue;
            }
            Validate.isTrue(Character.isJavaIdentifierPart(c), unexpected, c, pos, pkg);
        }

        pkg = pkg.replace('.', '/');
        final int last = pkg.length() - 1;
        if (pkg.charAt(last) == '/') {
            pkg = pkg.substring(0, last);
        }
        return pkg;
    }

    static Set<Class<?>> parseTypes(String types, ClassLoader cl) {
        final Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        for (String s : StringUtils.splitByWholeSeparatorPreserveAllTokens(types, ",")) {
            try {
                result.add(ClassUtils.getClass(cl, s.trim().replace('/', '.')));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return result;
    }
}
