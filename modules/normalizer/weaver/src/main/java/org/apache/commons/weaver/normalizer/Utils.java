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

    /**
     * From Commons lang 3.2; use when available.
     * <p>
     * Converts an array of byte into a long using the default (little endian, Lsb0) byte and
     * bit ordering.
     * </p>
     * 
     * @param src the byte array to convert
     * @param srcPos the position in {@code src}, in byte unit, from where to start the
     *            conversion
     * @param dstInit initial value of the destination long
     * @param dstPos the position of the lsb, in bits, in the result long
     * @param nBytes the number of bytes to convert
     * @return a long containing the selected bits
     * @throws NullPointerException if {@code src} is {@code null}
     * @throws IllegalArgumentException if {@code (nBytes-1)*8+dstPos >= 64}
     * @throws ArrayIndexOutOfBoundsException if {@code srcPos + nBytes > src.length}
     */
    public static long byteArrayToLong(final byte[] src, final int srcPos, final long dstInit, final int dstPos,
        final int nBytes) {
        if ((src.length == 0 && srcPos == 0) || 0 == nBytes) {
            return dstInit;
        }
        if ((nBytes - 1) * 8 + dstPos >= 64) {
            throw new IllegalArgumentException(
                "(nBytes-1)*8+dstPos is greather or equal to than 64");
        }
        long out = dstInit;
        int shift = 0;
        for (int i = 0; i < nBytes; i++ ) {
            shift = i * 8 + dstPos;
            final long bits = (0xffL & src[i + srcPos]) << shift;
            final long mask = 0xffL << shift;
            out = (out & ~mask) | bits;
        }
        return out;
    }

}
