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
package org.apache.commons.weaver.ant;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.DynamicElementNS;

/**
 * <p>Structure to allow inline specification of properties.</p>
 * <p>Example:
 * {pre}&lt;foo&gt;foo-value&lt;/foo&gt;
 * &lt;bar&gt;bar-value&lt;/bar&gt;
 * &lt;baz&gt;baz
 * -nextline-value&lt;/baz&gt;
 * {/pre}
 * </p>
 */
public class InlineProperties implements DynamicElementNS {
    /**
     * Represents a single inline property.
     */
    public final class InlineProperty {
        private final String name;

        private InlineProperty(final String name) {
            this.name = name;
        }

        /**
         * Add text to this property.
         * @param text to add
         */
        public void addText(final String text) {
            final String value;
            if (properties.containsKey(name)) {
                value = StringUtils.join(properties.getProperty(name), text);
            } else {
                value = text;
            }
            properties.setProperty(name, value);
        }
    }

    /**
     * {@link Properties} object maintained by the {@link InlineProperties}.
     */
    final Properties properties = new Properties();

    /**
     * Handle the specified nested element.
     * @param uri String URI
     * @param localName local element name
     * @param qName qualified name
     * @return InlineProperty
     */
    @Override
    public InlineProperty createDynamicElement(final String uri, final String localName, final String qName) {
        return new InlineProperty(localName);
    }
}