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
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.WeavableClass;
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

    private Privilizer privilizer;

    @Override
    public void configure(final List<String> classPath, final File target, final Properties config) {
        final URLClassLoader urlClassLoader = new URLClassLoader(URLArray.fromPaths(classPath));
        final ClassPool classPool = Assistant.createClassPool(urlClassLoader, target);
        final ModifiedClassWriter modifiedClassWriter = new ModifiedClassWriter() {

            @Override
            public void write(CtClass type) throws CannotCompileException, IOException {
                type.writeFile(target.getAbsolutePath());
            }
        };

        final Privilizer.Builder builder = new Privilizer.Builder(classPool, modifiedClassWriter);

        final String accessLevel = config.getProperty(CONFIG_ACCESS_LEVEL);
        if (StringUtils.isNotEmpty(accessLevel)) {
            builder.withTargetAccessLevel(AccessLevel.valueOf(accessLevel));
        }

        final String policyConfig = config.getProperty(CONFIG_POLICY);

        if (StringUtils.isNotEmpty(policyConfig)) {
            builder.withPolicy(Privilizer.Policy.valueOf(policyConfig));
        }

        privilizer = builder.build();
    }

    @Override
    public ScanRequest getScanRequest() {
        return new ScanRequest().add(WeaveInterest.of(Privileged.class, ElementType.METHOD)).add(
            WeaveInterest.of(Privilizing.class, ElementType.TYPE));
    }

    @Override
    public boolean process(ScanResult scanResult) {
        boolean result = false;
        for (WeavableClass<?> weavableClass : scanResult.getClasses()) {
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
}
