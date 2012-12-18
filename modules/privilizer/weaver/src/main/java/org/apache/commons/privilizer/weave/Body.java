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
package org.apache.commons.privilizer.weave;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.text.StrBuilder;

class Body implements CharSequence {
    private final String indent = "  ";
    private final StrBuilder content = new StrBuilder();
    private int level = 0;
    private boolean lineStarted;

    {
        startBlock();
    }

    public char charAt(int index) {
        return content.charAt(index);
    }

    public int length() {
        return content.length();
    }

    public CharSequence subSequence(int start, int end) {
        return content.subSequence(start, end);
    }

    @Override
    public String toString() {
        return content.toString();
    }

    Body append(char c) {
        content.append(c);
        return this;
    }

    Body append(String format, Object... args) {
        prepare();
        content.append(String.format(format, args));
        return this;
    }

    Body appendLine(String format, Object... args) {
        return append(format, args).appendNewLine();
    }

    Body appendNewLine() {
        content.appendNewLine();
        lineStarted = false;
        return this;
    }

    Body complete() {
        try {
            return endBlock();
        } finally {
            Validate.validState(level == 0);
        }
    }

    Body endBlock() {
        if (level < 1) {
            throw new IllegalStateException();
        }
        level--;
        prepare();
        append('}').appendNewLine();
        return this;
    }

    Body startBlock() {
        if (lineStarted) {
            append(' ');
        }
        level++;
        return append('{').appendNewLine();
    }

    Body startBlock(String format, Object... args) {
        append(format, args);
        return startBlock();
    }

    private void prepare() {
        if (!lineStarted) {
            lineStarted = true;
            content.append(StringUtils.repeat(indent, level));
        }
    }
}