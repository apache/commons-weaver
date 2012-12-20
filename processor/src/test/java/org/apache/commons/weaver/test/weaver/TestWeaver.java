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
package org.apache.commons.weaver.test.weaver;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.weaver.test.beans.TestAnnotation;
import org.junit.Assert;

import org.apache.commons.weaver.spi.Weaver;

/**
 */
public class TestWeaver implements Weaver
{
    public static boolean preWeaveExecuted = false;
    public static boolean postWeaveExecuted = false;
    public static List<Method> wovenMethods = new ArrayList<Method>();
    public static List<Class> wovenClasses = new ArrayList<Class>();

    @Override
    public void setLogger(Logger customLogger) {
        // do nothing
    }

    @Override
    public void configure(Map<String, Object> config)
    {
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());

        String configValue = (String) config.get("configKey");
        Assert.assertEquals("configValue", configValue);
    }

    @Override
    public List<Class<? extends Annotation>> getInterest()
    {
        List<Class<? extends Annotation>> interests = new ArrayList<Class<? extends Annotation>>();
        interests.add(TestAnnotation.class);
        return interests;
    }

    @Override
    public void preWeave()
    {
        preWeaveExecuted = true;
    }

    @Override
    public boolean weave(Class classToWeave, Class<? extends Annotation> processingAnnotation)
    {
        return wovenClasses.add(classToWeave);
    }

    @Override
    public boolean weave(Method methodToWeave, Class<? extends Annotation> processingAnnotation)
    {
        return wovenMethods.add(methodToWeave);
    }

    @Override
    public void postWeave()
    {
        postWeaveExecuted = true;
    }
}
