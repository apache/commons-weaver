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
package org.apache.commons.weaver;

import static org.apache.commons.weaver.test.beans.ComplexAnnotations.Stooge.CURLY;
import static org.apache.commons.weaver.test.beans.ComplexAnnotations.Stooge.LARRY;
import static org.apache.commons.weaver.test.beans.ComplexAnnotations.Stooge.MOE;
import static org.apache.commons.weaver.test.beans.ComplexAnnotations.Stooge.SHEMP;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.commons.weaver.test.WeaverTestBase;
import org.apache.commons.weaver.test.beans.AbstractTestBean;
import org.apache.commons.weaver.test.beans.ComplexAnnotations;
import org.apache.commons.weaver.test.beans.ComplexAnnotations.NestAnnotation;
import org.apache.commons.weaver.test.beans.ComplexAnnotations.Stooge;
import org.apache.commons.weaver.test.beans.ComplexAnnotations.TestAnnotation;
import org.apache.commons.weaver.test.beans.TestBeanInterface;
import org.apache.commons.weaver.test.beans.TestBeanWithClassAnnotation;
import org.apache.commons.weaver.test.beans.TestBeanWithMethodAnnotation;
import org.apache.commons.weaver.utils.URLArray;
import org.apache.xbean.finder.Annotated;
import org.apache.xbean.finder.archive.FileArchive;
import org.hamcrest.Matchers;
import org.junit.Test;

public class FinderTest extends WeaverTestBase {

    private Finder finder() {
        final ClassLoader classLoader = new URLClassLoader(URLArray.fromPaths(getClassPathEntries()));
        return new Finder(new FileArchive(classLoader, getTargetFolder()));
    }

    /**
     * The point of this is to prove that we can correctly hydate instances of
     * annotations with class retention.
     * 
     * @throws IOException
     */
    @Test
    public void testElements() throws IOException {
        addClassForScanning(ComplexAnnotations.class);
        Map<String, Annotated<Field>> fields = new HashMap<String, Annotated<Field>>();
        for (Annotated<Field> annotated : finder().withAnnotations().findAnnotatedFields(
            ComplexAnnotations.TestAnnotation.class)) {
            fields.put(annotated.get().getName(), annotated);
        }
        assertEquals(2, fields.size());

        TestAnnotation anno1 = fields.get("dummy1").getAnnotation(TestAnnotation.class);

        assertFalse(anno1.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, anno1.booleanValues()));
        assertEquals((byte) 0, anno1.byteValue());
        assertArrayEquals(new byte[] { 0 }, anno1.byteValues());
        assertEquals((char) 0, anno1.charValue());
        assertArrayEquals(new char[] { 0 }, anno1.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(anno1.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, anno1.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(anno1.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, anno1.floatValues()));
        assertEquals(0, anno1.intValue());
        assertArrayEquals(new int[] { 0 }, anno1.intValues());
        assertEquals(0L, anno1.longValue());
        assertArrayEquals(new long[] { 0L }, anno1.longValues());

        NestAnnotation nest1 = anno1.nest();
        assertFalse(nest1.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, nest1.booleanValues()));
        assertEquals((byte) 0, nest1.byteValue());
        assertArrayEquals(new byte[] { 0 }, nest1.byteValues());
        assertEquals((char) 0, nest1.charValue());
        assertArrayEquals(new char[] { 0 }, nest1.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(nest1.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, nest1.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(nest1.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, nest1.floatValues()));
        assertEquals(0, nest1.intValue());
        assertArrayEquals(new int[] { 0 }, nest1.intValues());
        assertEquals(0L, nest1.longValue());
        assertArrayEquals(new long[] { 0L }, nest1.longValues());
        assertEquals((short) 0, nest1.shortValue());
        assertArrayEquals(new short[] { 0 }, nest1.shortValues());
        assertSame(CURLY, nest1.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, SHEMP }, nest1.stooges());
        assertEquals("", nest1.string());
        assertArrayEquals(new String[] { "" }, nest1.strings());
        assertEquals(Object.class, nest1.type());
        assertArrayEquals(new Class[] { Object.class }, nest1.types());

        assertEquals(1, anno1.nests().length);
        NestAnnotation nest1_0 = anno1.nests()[0];
        assertFalse(nest1_0.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, nest1_0.booleanValues()));
        assertEquals((byte) 0, nest1_0.byteValue());
        assertArrayEquals(new byte[] { 0 }, nest1_0.byteValues());
        assertEquals((char) 0, nest1_0.charValue());
        assertArrayEquals(new char[] { 0 }, nest1_0.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(nest1_0.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, nest1_0.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(nest1_0.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, nest1_0.floatValues()));
        assertEquals(0, nest1_0.intValue());
        assertArrayEquals(new int[] { 0 }, nest1_0.intValues());
        assertEquals(0L, nest1_0.longValue());
        assertArrayEquals(new long[] { 0L }, nest1_0.longValues());
        assertEquals((short) 0, nest1_0.shortValue());
        assertArrayEquals(new short[] { 0 }, nest1_0.shortValues());
        assertSame(CURLY, nest1_0.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, SHEMP }, nest1_0.stooges());
        assertEquals("", nest1_0.string());
        assertArrayEquals(new String[] { "" }, nest1_0.strings());
        assertEquals(Object[].class, nest1_0.type());
        assertArrayEquals(new Class[] { Object[].class }, nest1_0.types());

        assertEquals((short) 0, anno1.shortValue());
        assertArrayEquals(new short[] { 0 }, anno1.shortValues());
        assertSame(SHEMP, anno1.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, CURLY }, anno1.stooges());
        assertEquals("", anno1.string());
        assertArrayEquals(new String[] { "" }, anno1.strings());
        assertEquals(Object.class, anno1.type());
        assertArrayEquals(new Class[] { Object.class }, anno1.types());

        TestAnnotation anno2 = fields.get("dummy2").getAnnotation(TestAnnotation.class);
        assertFalse(anno2.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, anno2.booleanValues()));
        assertEquals((byte) 0, anno2.byteValue());
        assertArrayEquals(new byte[] { 0 }, anno2.byteValues());
        assertEquals((char) 0, anno2.charValue());
        assertArrayEquals(new char[] { 0 }, anno2.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(anno2.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, anno2.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(anno2.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, anno2.floatValues()));
        assertEquals(0, anno2.intValue());
        assertArrayEquals(new int[] { 0 }, anno2.intValues());
        assertEquals(0L, anno2.longValue());
        assertArrayEquals(new long[] { 0L }, anno2.longValues());

        NestAnnotation nest2 = anno2.nest();
        assertFalse(nest2.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, nest2.booleanValues()));
        assertEquals((byte) 0, nest2.byteValue());
        assertArrayEquals(new byte[] { 0 }, nest2.byteValues());
        assertEquals((char) 0, nest2.charValue());
        assertArrayEquals(new char[] { 0 }, nest2.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(nest2.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, nest2.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(nest2.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, nest2.floatValues()));
        assertEquals(0, nest2.intValue());
        assertArrayEquals(new int[] { 0 }, nest2.intValues());
        assertEquals(0L, nest2.longValue());
        assertArrayEquals(new long[] { 0L }, nest2.longValues());
        assertEquals((short) 0, nest2.shortValue());
        assertArrayEquals(new short[] { 0 }, nest2.shortValues());
        assertSame(CURLY, nest2.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, SHEMP }, nest2.stooges());
        assertEquals("", nest2.string());
        assertArrayEquals(new String[] { "" }, nest2.strings());
        assertEquals(Object.class, nest2.type());
        assertArrayEquals(new Class[] { Object.class }, nest2.types());

        assertEquals(2, anno2.nests().length);
        NestAnnotation nest2_0 = anno2.nests()[0];
        assertFalse(nest2_0.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, nest2_0.booleanValues()));
        assertEquals((byte) 0, nest2_0.byteValue());
        assertArrayEquals(new byte[] { 0 }, nest2_0.byteValues());
        assertEquals((char) 0, nest2_0.charValue());
        assertArrayEquals(new char[] { 0 }, nest2_0.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(nest2_0.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, nest2_0.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(nest2_0.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, nest2_0.floatValues()));
        assertEquals(0, nest2_0.intValue());
        assertArrayEquals(new int[] { 0 }, nest2_0.intValues());
        assertEquals(0L, nest2_0.longValue());
        assertArrayEquals(new long[] { 0L }, nest2_0.longValues());
        assertEquals((short) 0, nest2_0.shortValue());
        assertArrayEquals(new short[] { 0 }, nest2_0.shortValues());
        assertSame(CURLY, nest2_0.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, SHEMP }, nest2_0.stooges());
        assertEquals("", nest2_0.string());
        assertArrayEquals(new String[] { "" }, nest2_0.strings());
        assertEquals(Object[].class, nest2_0.type());
        assertArrayEquals(new Class[] { Object[].class }, nest2_0.types());

        NestAnnotation nest2_1 = anno2.nests()[1];
        assertFalse(nest2_1.booleanValue());
        assertTrue(Arrays.equals(new boolean[] { false }, nest2_1.booleanValues()));
        assertEquals((byte) 0, nest2_1.byteValue());
        assertArrayEquals(new byte[] { 0 }, nest2_1.byteValues());
        assertEquals((char) 0, nest2_1.charValue());
        assertArrayEquals(new char[] { 0 }, nest2_1.charValues());
        assertEquals(Double.valueOf(0.0), Double.valueOf(nest2_1.doubleValue()));
        assertTrue(Arrays.equals(new double[] { 0.0 }, nest2_1.doubleValues()));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(nest2_1.floatValue()));
        assertTrue(Arrays.equals(new float[] { 0.0f }, nest2_1.floatValues()));
        assertEquals(0, nest2_1.intValue());
        assertArrayEquals(new int[] { 0 }, nest2_1.intValues());
        assertEquals(0L, nest2_1.longValue());
        assertArrayEquals(new long[] { 0L }, nest2_1.longValues());
        assertEquals((short) 0, nest2_1.shortValue());
        assertArrayEquals(new short[] { 0 }, nest2_1.shortValues());
        assertSame(CURLY, nest2_1.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, SHEMP }, nest2_1.stooges());
        assertEquals("", nest2_1.string());
        assertArrayEquals(new String[] { "" }, nest2_1.strings());
        assertEquals(Object[].class, nest2_1.type());
        assertArrayEquals(new Class[] { Object[].class }, nest2_1.types());

        assertEquals((short) 0, anno2.shortValue());
        assertArrayEquals(new short[] { 0 }, anno2.shortValues());
        assertSame(SHEMP, anno2.stooge());
        assertArrayEquals(new Stooge[] { MOE, LARRY, CURLY }, anno2.stooges());
        assertEquals("", anno2.string());
        assertArrayEquals(new String[] { "" }, anno2.strings());
        assertEquals(Object.class, anno2.type());
        assertArrayEquals(new Class[] { Object.class }, anno2.types());
    }

    @Test
    public void testObjectMethods() throws IOException {
        addClassForScanning(ComplexAnnotations.class);
        for (Annotated<Field> annotated : finder().withAnnotations().findAnnotatedFields(
            ComplexAnnotations.TestAnnotation.class)) {
            TestAnnotation anno = annotated.getAnnotation(TestAnnotation.class);
            assertFalse(anno.toString().isEmpty());
            assertFalse(anno.hashCode() == 0);
            assertTrue(anno.equals(anno));
        }
    }

    @Test
    public void testFindAssignableTypes() throws IOException {
        addClassForScanning(TestBeanInterface.class);
        addClassForScanning(AbstractTestBean.class);
        addClassForScanning(TestBeanWithClassAnnotation.class);
        addClassForScanning(TestBeanWithMethodAnnotation.class);

        final Set<Class<?>> implementors = new HashSet<Class<?>>();
        for (Annotated<Class<?>> annotated : finder().withAnnotations().findAssignableTypes(TestBeanInterface.class)) {
            implementors.add(annotated.get());
        }
        assertEquals(1, implementors.size());
        assertTrue(implementors.contains(TestBeanWithClassAnnotation.class));

        final Set<Class<?>> subclasses = new HashSet<Class<?>>();
        for (Annotated<Class<?>> annotated : finder().withAnnotations().findAssignableTypes(AbstractTestBean.class)) {
            subclasses.add(annotated.get());
        }
        assertEquals(2, subclasses.size());
        assertTrue(subclasses.contains(TestBeanWithClassAnnotation.class));
        assertTrue(subclasses.contains(TestBeanWithMethodAnnotation.class));
    }

    @Test
    public void testFindAllTypes() throws IOException {
        addClassForScanning(TestBeanInterface.class);
        addClassForScanning(AbstractTestBean.class);
        addClassForScanning(TestBeanWithClassAnnotation.class);
        addClassForScanning(TestBeanWithMethodAnnotation.class);

        List<Annotated<Class<?>>> allClasses = finder().withAnnotations().getAllClasses();
        assertThat(extract(allClasses), Matchers.<Class<?>> containsInAnyOrder(TestBeanInterface.class,
            AbstractTestBean.class, TestBeanWithClassAnnotation.class, TestBeanWithMethodAnnotation.class));
    }

    private List<Class<?>> extract(List<Annotated<Class<?>>> input) {
        Validate.noNullElements(input);
        if (input.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Class<?>> result = new ArrayList<Class<?>>(input.size());
        for (Annotated<Class<?>> c : input) {
            result.add(c.get());
        }
        return result;
    }
}
