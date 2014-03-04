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
package org.apache.commons.weaver.test;

import java.io.File;
import java.util.Properties;

import org.apache.commons.weaver.CleanProcessor;
import org.apache.commons.weaver.test.beans.TestBeanWithClassAnnotation;
import org.apache.commons.weaver.test.beans.TestBeanWithMethodAnnotation;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test the {@link CleanProcessor}
 */
public class CleanProcessorTest extends WeaverTestBase {

    @Test
    public void testWeaveVisiting() throws Exception {
        addClassForScanning(TestBeanWithMethodAnnotation.class);
        addClassForScanning(TestBeanWithClassAnnotation.class);

        final Properties config = new Properties();
        config.put("configKey", "configValue");

        final CleanProcessor cp = new CleanProcessor(getClassPathEntries(), getTargetFolder(), config);
        cp.clean();

        Assert.assertFalse(new File(getTargetFolder(), TestBeanWithMethodAnnotation.class.getName().replace('.',
            File.separatorChar)
            + ".class").exists());
        Assert.assertFalse(new File(getTargetFolder(), TestBeanWithClassAnnotation.class.getName().replace('.',
            File.separatorChar)
            + ".class").exists());
    }
}
