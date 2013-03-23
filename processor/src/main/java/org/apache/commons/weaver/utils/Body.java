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
package org.apache.commons.weaver.utils;

import java.util.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.text.StrBuilder;

/**
 * Represents a basic block level Java code construct for simple formatted code generation.
 */
public class Body implements CharSequence {
    private static Logger getLogger(Object category) {
        if (category == null) {
            return Logger.getAnonymousLogger();
        }
        if (category instanceof CharSequence) {
            return Logger.getLogger(category.toString());
        }
        return Logger.getLogger(category.getClass().getName());
    }

    private final Object category;
    private final String message;
    private final Object[] args;
    private final String indent = "  ";
    private final StrBuilder content = new StrBuilder();
    private int level = 0;
    private boolean lineStarted;

    /**
     * Create a new Body instance.
     * 
     * @param category
     *            used for logging
     * @param message
     *            {@link Formatter} style
     * @param args
     *            to message
     */
    public Body(Object category, String message, Object... args) {
        startBlock();
        this.category = category;
        this.message = message;
        this.args = args;
    }

    /**
     * @param index
     * @return char
     * @see CharSequence#charAt(int)
     */
    public char charAt(int index) {
        return content.charAt(index);
    }

    /**
     * @return int
     * @see CharSequence#length()
     */
    public int length() {
        return content.length();
    }

    /**
     * @param start
     * @param end
     * @return CharSequence
     * @see CharSequence#subSequence(int, int)
     */
    public CharSequence subSequence(int start, int end) {
        return content.subSequence(start, end);
    }

    @Override
    public String toString() {
        return content.toString();
    }

    /**
     * Append a character.
     * 
     * @param c
     * @return this
     */
    public Body append(char c) {
        content.append(c);
        return this;
    }

    /**
     * Append a formatted message.
     * 
     * @param format
     * @param args
     * @return this
     * @see Formatter
     */
    public Body append(String format, Object... args) {
        prepare();
        content.append(String.format(format, args));
        return this;
    }

    /**
     * Append a formatted line.
     * 
     * @param format
     * @param args
     * @return this
     * @see Formatter
     */
    public Body appendLine(String format, Object... args) {
        return append(format, args).appendNewLine();
    }

    /**
     * Append a platform-specific newline.
     * 
     * @return this
     */
    public Body appendNewLine() {
        content.appendNewLine();
        lineStarted = false;
        return this;
    }

    /**
     * Complete this Body and, if enabled at FINE level, log it.
     * 
     * @return this
     * @see Level
     */
    public Body complete() {
        try {
            return endBlock();
        } finally {
            Validate.validState(level == 0);
            final Logger log = getLogger(category);
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format(message, args));
                log.fine(toString());
            }
        }
    }

    /**
     * End the innermost open block and dedent.
     * 
     * @return this
     */
    public Body endBlock() {
        if (level < 1) {
            throw new IllegalStateException();
        }
        level--;
        prepare();
        append('}').appendNewLine();
        return this;
    }

    /**
     * Start a new block and indent.
     * 
     * @return this
     */
    public Body startBlock() {
        if (lineStarted) {
            append(' ');
        }
        level++;
        return append('{').appendNewLine();
    }

    /**
     * Start a new block with some formatted text (e.g. if statement) and indent.
     * 
     * @param format
     * @param args
     * @return this
     * @see Formatter
     */
    public Body startBlock(String format, Object... args) {
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
