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
package org.apache.commons.weaver.ant;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.DynamicElement;

/**
 * Structure to allow the inline specification of weaver properties.
 */
public class InlineProperties implements DynamicElement {
    /**
     * Represents a single inline property.
     */
    public class InlineProperty {
        private final String name;

        private InlineProperty(String name) {
            this.name = name;
        }

        public void addText(String text) {
            if (properties.containsKey(name)) {
                text = StringUtils.join(properties.getProperty(name), text);
            }
            properties.setProperty(name, text);
        }
    }

    final Properties properties = new Properties();

    public InlineProperty createDynamicElement(String name) {
        return new InlineProperty(name);
    }
}