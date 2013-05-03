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

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeavableMethod;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.test.beans.AbstractTestBean;
import org.apache.commons.weaver.test.beans.TestAnnotation;
import org.apache.commons.weaver.test.beans.TestBeanInterface;
import org.junit.Assert;

/**
 * 
 */
public class TestWeaver implements Weaver {
    public static List<Method> wovenMethods = new ArrayList<Method>();
    public static List<Class<?>> wovenClasses = new ArrayList<Class<?>>();
    public static List<Class<?>> implementors = new ArrayList<Class<?>>();
    public static List<Class<?>> subclasses = new ArrayList<Class<?>>();

    @Override
    public boolean process(WeaveEnvironment environment, Scanner scanner) {
        Assert.assertNotNull(environment.config);
        Assert.assertEquals(1, environment.config.size());

        String configValue = environment.config.getProperty("configKey");

        Assert.assertEquals("configValue", configValue);
        boolean result = false;

        final ScanRequest scanRequest =
            new ScanRequest().add(WeaveInterest.of(TestAnnotation.class, ElementType.TYPE))
                .add(WeaveInterest.of(TestAnnotation.class, ElementType.METHOD))
                .addSupertypes(AbstractTestBean.class, TestBeanInterface.class);

        final ScanResult scanResult = scanner.scan(scanRequest);

        for (WeavableClass<?> weavableClass : scanResult.getClasses().with(TestAnnotation.class)) {
            if (wovenClasses.add(weavableClass.getTarget())) {
                result = true;
            }
        }
        for (WeavableMethod<?> weavableMethod : scanResult.getMethods().with(TestAnnotation.class)) {
            if (wovenMethods.add(weavableMethod.getTarget())) {
                result = true;
            }
        }
        for (WeavableClass<?> weavableClass : scanResult.getClassesAssignableTo(TestBeanInterface.class)) {
            if (implementors.add(weavableClass.getTarget())) {
                result = true;
            }
        }
        for (WeavableClass<?> weavableClass : scanResult.getClassesAssignableTo(AbstractTestBean.class)) {
            if (subclasses.add(weavableClass.getTarget())) {
                result = true;
            }
        }
        return result;
    }

}
