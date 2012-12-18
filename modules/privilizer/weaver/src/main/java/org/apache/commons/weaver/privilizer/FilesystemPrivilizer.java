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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.FileArchive;


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
     * Clear the way by deleting classfiles woven with a different
     * {@link Policy}.
     * 
     * @throws NotFoundException
     */
    public void prepare() throws NotFoundException {
        info("preparing %s; policy = %s", target, policy);
        final Set<File> toDelete = new TreeSet<File>();
        for (final Class<?> type : getDeclaringClasses(findPrivilegedMethods())) {
            final CtClass ctClass = classPool.get(type.getName());
            final String policyValue = toString(ctClass.getAttribute(generateName(POLICY_NAME)));
            if (policyValue == null || policyValue.equals(policy.name())) {
                continue;
            }
            debug("class %s previously woven with policy %s", type.getName(), policyValue);
            final File packageDir =
                new File(target, StringUtils.replaceChars(ctClass.getPackageName(), '.', File.separatorChar));

            // simple classname of outermost class, plus any inner classes:
            final String pattern =
                new StringBuilder(getOutermost(type).getSimpleName()).append("(\\$.+)??\\.class").toString();

            debug("searching %s for pattern '%s'", packageDir.getAbsolutePath(), pattern);
            toDelete.addAll(FileUtils.listFiles(packageDir, new RegexFileFilter(pattern), null));
        }
        if (toDelete.isEmpty()) {
            return;
        }
        info("Deleting %s files...", toDelete.size());
        debug(toDelete.toString());
        for (File f : toDelete) {
            if (!f.delete()) {
                debug("Failed to delete %s", f);
            }
        }
    }

    /**
     * Weave all {@link Privileged} methods found.
     * 
     * @throws NotFoundException
     * @throws IOException
     * @throws CannotCompileException
     * @throws ClassNotFoundException
     */
    public void weaveAll() throws NotFoundException, IOException, CannotCompileException, ClassNotFoundException {
        int woven = 0;
        for (final Class<?> type : getDeclaringClasses(findPrivilegedMethods())) {
            if (weave(classPool.get(type.getName()))) {
                woven++;
            }
        }
        if (woven > 0) {
            info("Wove %s classes.", woven);
        }
    }

    @Override
    protected ClassFileWriter getClassFileWriter() {
        return classFileWriter;
    }

    private List<Method> findPrivilegedMethods() {
        final AnnotationFinder annotationFinder = new AnnotationFinder(new FileArchive(classpath, target), false);
        return annotationFinder.findAnnotatedMethods(Privileged.class);
    }
}
