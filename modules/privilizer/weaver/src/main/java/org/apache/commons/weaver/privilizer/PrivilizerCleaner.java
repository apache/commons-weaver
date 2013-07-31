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
package org.apache.commons.weaver.privilizer;

import java.io.File;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.privilizer.Privilizer;
import org.apache.commons.weaver.spi.Cleaner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Removes classes privilized with a different policy.
 */
public class PrivilizerCleaner implements Cleaner {
    private static final int ASM_FLAGS = ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES;
    private static final Logger LOG = Logger.getLogger(PrivilizerCleaner.class.getName());

    @Override
    public boolean clean(WeaveEnvironment environment, Scanner scanner) {
        final Privilizer privilizer = new Privilizer(environment);

        final List<String> toDelete = new ArrayList<String>();

        final ScanRequest scanRequest = new ScanRequest().add(WeaveInterest.of(Privilized.class, ElementType.TYPE));

        LOG.log(Level.FINE, "Cleaning classes privilized with policy other than {0}", privilizer.policy);
        for (WeavableClass<?> weavableClass : scanner.scan(scanRequest).getClasses().with(Privilized.class)) {
            final Policy privilizedPolicy = Policy.valueOf(weavableClass.getAnnotation(Privilized.class).value());
            if (privilizedPolicy == privilizer.policy) {
                continue;
            }
            final String className = weavableClass.getTarget().getName();
            LOG.log(Level.FINE, "Class {0} privilized with {1}; deleting.",
                new Object[] { className, privilizedPolicy });

            try {
                final ClassReader classReader = new ClassReader(privilizer.fileArchive.getBytecode(className));
                classReader.accept(new ClassVisitor(Opcodes.ASM4) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName,
                        String[] interfaces) {
                        toDelete.add(name);
                    }

                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        if (toDelete.contains(outerName)) {
                            toDelete.add(name);
                        }
                    }
                }, ASM_FLAGS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        boolean result = false;
        for (String className : toDelete) {
            final File classfile = new File(environment.target, toResourcePath(className));
            final boolean success = classfile.delete();
            LOG.log(Level.FINE, "Deletion of {0} was {1}.", new Object[] { classfile,
                success ? "successful" : "unsuccessful" });
            result |= success;
        }
        return result;
    }

    private static String toResourcePath(String className) {
        return className.replace('.', '/') + ".class";
    }

}
