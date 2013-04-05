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
package org.apache.commons.weaver.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.weaver.spi.Weaver;

/**
 * Helper for Javassist-based {@link Weaver} implementations.
 */
public class Assistant {

    /**
     * Default prefix.
     */
    public static final String DEFAULT_PREFIX = "_weaver_assisted_";

    private class LazyExceptionsBody extends Body {

        private LazyExceptionsBody(String message, Object... args) {
            super(prefix, message, args);
            startBlock("try");
        }

        @Override
        public Body complete() {
            final String e = generateName("lazyException");
            endBlock()
                .startBlock("catch (Exception %s)", e)
                .appendLine(
                    "throw %1$s instanceof RuntimeException ? (RuntimeException) %1$s : new RuntimeException(%1$s);", e)
                .endBlock();
            return super.complete();
        }
    }

    private final ClassPool classPool;
    private final String prefix;

    /**
     * Create a new {@link Assistant} with the specified {@link ClassPool} and {@code DEFAULT_PREFIX}.
     * 
     * @param classPool
     */
    public Assistant(ClassPool classPool) {
        this(classPool, DEFAULT_PREFIX);
    }

    /**
     * Create a new {@link Assistant}.
     * 
     * @param classPool
     *            used
     * @param prefix
     *            used for generated elements.
     */
    public Assistant(ClassPool classPool, String prefix) {
        super();
        this.classPool = classPool;
        this.prefix = prefix;
    }

    /**
     * Generate a name.
     * 
     * @param name
     * @return String
     */
    public String generateName(String name) {
        return new StringBuilder(prefix).append(name).toString();
    }

    /**
     * Generate a private method to read fields.
     * 
     * @param target
     * @return CtMethod
     */
    public CtMethod fieldReader(CtClass target) {
        final String name = generateName("readField");

        final CtClass[] params = types(Class.class, String.class, Object.class);
        try {
            return target.getDeclaredMethod(name, params);
        } catch (NotFoundException e) {
        }
        try {
            final CtMethod result =
                CtNewMethod.make(
                    new StringBuilder("private static Object ")
                        .append(name)
                        .append("(Class type, String name, Object instance)")
                        .append(
                            new LazyExceptionsBody("fieldReader").appendLine(
                                "return type.getDeclaredField(name).get(instance);").complete()).toString(), target);

            target.addMethod(result);
            return result;
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate a private method to write fields.
     * 
     * @param target
     * @return CtMethod
     */
    public CtMethod fieldWriter(CtClass target) {
        final String name = generateName("writeField");

        final CtClass[] params = types(Class.class, String.class, Object.class, Object.class);
        try {
            return target.getDeclaredMethod(name, params);
        } catch (NotFoundException e) {
        }
        try {
            final CtMethod result =
                CtNewMethod.make(
                    new StringBuilder("private static void ")
                        .append(name)
                        .append("(Class type, String name, Object instance, Object value)")
                        .append(
                            new LazyExceptionsBody("fieldWriter").appendLine(
                                "type.getDeclaredField(name).set(instance, value);").complete()).toString(), target);
            target.addMethod(result);
            return result;
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    public String callPushFieldAccess(CtClass target, Iterable<CtField> fields) {
        final Body setAccessible = new LazyExceptionsBody("pushFieldAccess");

        final String flds = generateName("flds");

        setAccessible.appendLine("final java.util.List %s = new java.util.ArrayList();", flds);

        final String fld = generateName("fld");

        if (fields != null) {
            setAccessible.appendLine("java.lang.reflect.Field %s;", fld);
            final Set<Pair<String, String>> uniqueFields = new HashSet<Pair<String, String>>();
            for (CtField field : fields) {
                if (Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                final Pair<String, String> pair = Pair.of(field.getDeclaringClass().getName(), field.getName());
                if (uniqueFields.add(pair)) {
                    setAccessible.appendLine("%s = %s.class.getDeclaredField(\"%s\");", fld, pair.getLeft(),
                        pair.getRight());
                    setAccessible.startBlock("if (!%s.isAccessible())", fld).appendLine("%s.add(%s);", flds, fld)
                        .endBlock();
                }
            }
        }

        final String fieldArray = generateName("fieldArray");
        setAccessible
            .appendLine(
                "final java.lang.reflect.Field[] %1$s = %2$s.toArray(new java.lang.reflect.Field[%2$s.size()]);",
                fieldArray, flds).appendLine("%s(%s);", pushFieldAccess(target).getName(), fieldArray).complete();

        return setAccessible.toString();
    }

    public CtMethod pushFieldAccess(CtClass target) {
        final String name = generateName("pushFieldAccess");

        final CtClass[] params = types(new Field[0].getClass());
        try {
            return target.getDeclaredMethod(name, params);
        } catch (NotFoundException e) {
        }

        final String stk = generateName("stk");
        final String fieldAccessStack = getFieldAccessStack(target).getName();

        Body body =
            new Body(prefix, "pushFieldAccess").appendLine(
                "java.lang.reflect.AccessibleObject.setAccessible(fields, true);").appendLine(
                "java.util.Stack %s = (java.util.Stack) %s.get();", stk, fieldAccessStack);

        body.startBlock("if (%s == null)", stk).appendLine("%s = new java.util.Stack();", stk)
            .appendLine("%s.set(%s);", fieldAccessStack, stk).endBlock();

        body.appendLine("%s.push(fields);", stk).complete();

        try {
            final CtMethod result =
                CtNewMethod.make(
                    new StringBuilder("private static void ").append(name).append("(java.lang.reflect.Field[] fields)")
                        .append(body).toString(), target);
            target.addMethod(result);
            return result;
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    public CtMethod popFieldAccess(CtClass target) {
        final String name = generateName("popFieldAccess");

        final CtClass[] params = new CtClass[0];
        try {
            return target.getDeclaredMethod(name, params);
        } catch (NotFoundException e) {
        }

        final String stk = generateName("stk");
        final String fieldAccessStack = getFieldAccessStack(target).getName();
        final String fieldArray = generateName("fieldArray");

        final Body body = new Body(prefix, "popFieldAccess");
        body.appendLine("java.util.Stack %s = (java.util.Stack) %s.get();", stk, fieldAccessStack)
            .appendLine("java.lang.reflect.Field[] %s = (java.lang.reflect.Field[]) %s.pop();", fieldArray, stk)
            .appendLine("java.lang.reflect.AccessibleObject.setAccessible(%s, false);", fieldArray);
        body.startBlock("if (%s.isEmpty())", stk).appendLine("%s.remove();", fieldAccessStack).endBlock().complete();

        try {
            final CtMethod result =
                CtNewMethod.make(new StringBuilder("private static void ").append(name).append("()").append(body)
                    .toString(), target);
            target.addMethod(result);
            return result;
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    private CtField getFieldAccessStack(CtClass target) {
        final String fieldAccessStackName = generateName("fieldAccessStack");

        try {
            return target.getField(fieldAccessStackName);
        } catch (Exception e) {
            final CtField result;
            try {
                result = new CtField(classPool.get(ThreadLocal.class.getName()), fieldAccessStackName, target);
                result.setModifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL);
                target.addField(result, "new ThreadLocal()");
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
            return result;
        }
    }

    private CtClass[] types(Class<?>... types) {
        final CtClass[] result = new CtClass[Validate.noNullElements(types).length];
        int index = 0;
        for (Class<?> type : types) {
            try {
                result[index++] = classPool.get(type.getName());
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

}
