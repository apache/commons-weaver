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
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import org.apache.commons.lang3.Validate;


/**
 * Handles weaving of methods annotated with {@link Privileged}.
 */
public class FilesystemPrivilizer extends Privilizer<FilesystemPrivilizer> {
    private static ClassPool createClassPool(ClassLoader classpath, File target) {
        final ClassPool result = new ClassPool();
        try {
            result.appendClassPath(validTarget(target).getAbsolutePath());
            result.appendClassPath(new LoaderClassPath(classpath));
            result.appendPathList(System.getProperty("java.class.path"));
        } catch (final NotFoundException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private static Set<Class<?>> getDeclaringClasses(Iterable<Method> methods) {
        final Set<Class<?>> declaringClasses = new HashSet<Class<?>>();
        for (final Method method : methods) {
            declaringClasses.add(method.getDeclaringClass());
        }
        return declaringClasses;
    }

    private static Class<?> getOutermost(Class<?> type) {
        Class<?> enclosing = type.getEnclosingClass();
        return enclosing == null ? type : getOutermost(enclosing);
    }

    private static File validTarget(File target) {
        Validate.notNull(target, "target");
        Validate.isTrue(target.isDirectory(), "not a directory");
        return target;
    }

    private final ClassLoader classpath;

    private final File target;

    private final ClassFileWriter classFileWriter = new ClassFileWriter() {
        @Override
        public void write(CtClass type) throws CannotCompileException, IOException {
            type.writeFile(target.getAbsolutePath());
        }
    };

    public FilesystemPrivilizer(ClassLoader classpath, File target) {
        super(createClassPool(classpath, target));
        this.classpath = classpath;
        this.target = target;
    }

    public FilesystemPrivilizer(Policy policy, ClassLoader classpath, File target) {
        super(policy, createClassPool(classpath, target));
        this.classpath = classpath;
        this.target = target;
    }

    /**
     * Weave all {@link Privileged} methods found.
     * 
     * @throws NotFoundException
     * @throws IOException
     * @throws CannotCompileException
     * @throws ClassNotFoundException
     */
    public boolean weaveClass(Class<?> clazz)
            throws NotFoundException, IOException, CannotCompileException, ClassNotFoundException, IllegalAccessException {
        return weave(classPool.get(clazz.getName()));
    }

    @Override
    protected ClassFileWriter getClassFileWriter() {
        return classFileWriter;
    }

}
