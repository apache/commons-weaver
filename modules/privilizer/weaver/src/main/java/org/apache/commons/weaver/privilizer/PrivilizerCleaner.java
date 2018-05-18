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
package org.apache.commons.weaver.privilizer;

import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Cleaner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

/**
 * Removes classes privilized with a different policy.
 */
public class PrivilizerCleaner implements Cleaner {

    @Override
    public boolean clean(final WeaveEnvironment environment, final Scanner scanner) {
        final Privilizer privilizer = new Privilizer(environment);

        final List<String> toDelete = new ArrayList<>();

        final ScanRequest scanRequest = new ScanRequest().add(WeaveInterest.of(Privilized.class, ElementType.TYPE));

        environment.debug("Cleaning classes privilized with policy other than %s", privilizer.policy);
        for (final WeavableClass<?> weavableClass : scanner.scan(scanRequest).getClasses().with(Privilized.class)) {
            final Policy privilizedPolicy = Policy.valueOf(weavableClass.getAnnotation(Privilized.class).value());
            if (privilizedPolicy == privilizer.policy) {
                continue;
            }
            final String className = weavableClass.getTarget().getName();
            environment.debug("Class %s privilized with %s; deleting.", className, privilizedPolicy);

            try (InputStream bytecode = privilizer.env.getClassfile(className).getInputStream()) {
                final ClassReader classReader = new ClassReader(bytecode);
                classReader.accept(new ClassVisitor(Privilizer.ASM_VERSION) {
                    @Override
                    @SuppressWarnings("PMD.UseVarargs") // overridden method
                    public void visit(final int version, final int access, final String name, final String signature,
                        final String superName, final String[] interfaces) {
                        toDelete.add(name);
                    }

                    @Override
                    public void visitInnerClass(final String name, final String outerName, final String innerName,
                        final int access) {
                        if (toDelete.contains(outerName)) {
                            toDelete.add(name);
                        }
                    }
                }, ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
        boolean result = false;
        for (final String className : toDelete) {
            final String resourcePath = toResourcePath(className);
            final boolean success = environment.deleteResource(resourcePath);
            environment.debug("Deletion of resource %s was %ssuccessful.", resourcePath, success ? "" : "un");
            result |= success;
        }
        return result;
    }

    private static String toResourcePath(final String className) {
        return className.replace('.', '/') + ".class";
    }

}
