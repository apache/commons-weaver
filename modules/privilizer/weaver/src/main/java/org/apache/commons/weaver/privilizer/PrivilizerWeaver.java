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

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.net.URLClassLoader;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveEnvironment;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.privilizer.Privilizer.ModifiedClassWriter;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.Assistant;
import org.apache.commons.weaver.utils.URLArray;

/**
 * Weaver which adds doPrivileged blocks for each method annotated with {@link Privileged}. An instance of this class
 * will automatically get picked up by the {@link org.apache.commons.weaver.WeaveProcessor} via the
 * {@link java.util.ServiceLoader}.
 */
public class PrivilizerWeaver implements Weaver {
    public static final String CONFIG_WEAVER = "privilizer.";
    public static final String CONFIG_ACCESS_LEVEL = CONFIG_WEAVER + "accessLevel";
    public static final String CONFIG_POLICY = CONFIG_WEAVER + "policy";

    @Override
    public boolean process(WeaveEnvironment environment, Scanner scanner) {
        boolean result = false;
        final Privilizer privilizer = buildPrivilizer(environment);

        final ScanRequest scanRequest =
            new ScanRequest().add(WeaveInterest.of(Privileged.class, ElementType.METHOD)).add(
                WeaveInterest.of(Privilizing.class, ElementType.TYPE));

        for (WeavableClass<?> weavableClass : scanner.scan(scanRequest).getClasses()) {
            try {
                result =
                    privilizer.weaveClass(weavableClass.getTarget(), weavableClass.getAnnotation(Privilizing.class))
                        | result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private Privilizer buildPrivilizer(final WeaveEnvironment env) {
        final URLClassLoader urlClassLoader = new URLClassLoader(URLArray.fromPaths(env.classpath));
        final ClassPool classPool = Assistant.createClassPool(urlClassLoader, env.target);
        final ModifiedClassWriter modifiedClassWriter = new ModifiedClassWriter() {

            @Override
            public void write(CtClass type) throws CannotCompileException, IOException {
                type.writeFile(env.target.getAbsolutePath());
            }
        };

        final Privilizer.Builder builder = new Privilizer.Builder(classPool, modifiedClassWriter);

        final String accessLevel = env.config.getProperty(CONFIG_ACCESS_LEVEL);
        if (StringUtils.isNotEmpty(accessLevel)) {
            builder.withTargetAccessLevel(AccessLevel.valueOf(accessLevel));
        }

        final String policyConfig = env.config.getProperty(CONFIG_POLICY);

        if (StringUtils.isNotEmpty(policyConfig)) {
            builder.withPolicy(Privilizer.Policy.valueOf(policyConfig));
        }

        return builder.build();
    }

}