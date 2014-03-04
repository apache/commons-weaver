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
package org.apache.commons.weaver.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Base class for Weaver tests.
 */
public abstract class WeaverTestBase {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File targetFolder;

    private List<String> classPathEntries;

    private static final String TARGET_FOLDER = "target";

    public void cleanup() {
        targetFolder = null;
    }

    /**
     * Add a class (and its inner classes) to the temporary folder.
     * 
     * @param clazz
     */
    protected void addClassForScanning(Class<?> clazz) throws IOException {
        String clazzDirName = clazz.getPackage().getName().replace(".", "/");
        File targetDirFile = new File(getTargetFolder(), clazzDirName);
        targetDirFile.mkdirs();

        String fileName = baseName(clazz) + ".class";
        String clazzFileName = clazzDirName + "/" + fileName;
        URL clazzUrl = getClass().getClassLoader().getResource(clazzFileName);
        File targetClazzFile = new File(targetDirFile, fileName);

        byte[] buffer = new byte[0xFFFF];

        FileOutputStream fos = new FileOutputStream(targetClazzFile);

        InputStream inputStream = clazzUrl.openStream();
        int len;
        while ((len = inputStream.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.flush();
        fos.close();

        for (Class<?> inner : clazz.getClasses()) {
            addClassForScanning(inner);
        }
    }

    private String baseName(Class<?> clazz) {
        if (clazz.getDeclaringClass() == null) {
            return clazz.getSimpleName();
        }
        final StringBuilder result = new StringBuilder();
        Class<?> current = clazz;
        while (current != null) {
            if (result.length() > 0) {
                result.insert(0, '$');
            }
            result.insert(0, current.getSimpleName());
            current = current.getDeclaringClass();
        }
        return result.toString();
    }

    /**
     * Resolves the 'target' folder where the classes should get placed
     */
    protected File getTargetFolder() {
        if (targetFolder == null) {
            targetFolder = new File(temporaryFolder.getRoot(), TARGET_FOLDER);
        }
        return targetFolder;
    }

    protected List<String> getClassPathEntries() {
        if (classPathEntries == null) {
            classPathEntries = new ArrayList<String>(1);
            try {
                classPathEntries.add(getTargetFolder().getCanonicalPath());
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        return classPathEntries;
    }

}
