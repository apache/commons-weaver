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
package org.apache.commons.weaver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.model.WeaveEnvironment;

class LocalWeaveEnvironment extends WeaveEnvironment {

    private final File target;

    protected LocalWeaveEnvironment(final File target, final ClassLoader classLoader, final Properties config,
        final Logger log) {
        super(classLoader, config, log);
        Validate.notNull(target, "target");
        this.target = target;
    }

    @Override
    public boolean deleteResource(final String name) {
        return new File(target, name).delete();
    }

    @Override
    protected OutputStream getOutputStream(final String resourceName) throws IOException {
        final File file = new File(target, resourceName);
        final File parent = file.getParentFile();
        if (parent.exists()) {
            Validate.validState(parent.isDirectory(), "Cannot write %s to non-directory parent", file);
        } else {
            Validate.validState(parent.mkdirs(), "Unable to create output directory %s", parent);
        }
        return new FileOutputStream(file);
    }
}
