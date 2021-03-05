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

import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Cleaner;
import org.apache.commons.weaver.test.beans.TestAnnotation;

public class TestCleaner implements Cleaner {

    @Override
    public boolean clean(final WeaveEnvironment environment, final Scanner scanner) {
        boolean result = false;

        final ScanRequest scanRequest =
            new ScanRequest().add(WeaveInterest.of(TestAnnotation.class, ElementType.TYPE)).add(
                WeaveInterest.of(TestAnnotation.class, ElementType.METHOD));

        for (final WeavableClass<?> weavableClass : scanner.scan(scanRequest).getClasses()) {
            if (!environment.deleteClassfile(weavableClass.getTarget())) {
                break;
            }
            result = true;
        }
        return result;
    }

}
