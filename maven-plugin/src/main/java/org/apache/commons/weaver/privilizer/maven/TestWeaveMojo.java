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
package org.apache.commons.weaver.privilizer.maven;

import org.apache.commons.weaver.privilizer.Privileged;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;


/**
 * Goal to weave test classes with {@link SecurityManager} handling code for methods marked with
 * the {@link Privileged} annotation.
 */
@Mojo(name = "test-weave", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, requiresDependencyCollection = ResolutionScope.TEST)
public class TestWeaveMojo extends TestPrivilegedMojo {

    @Override
    public void execute() throws MojoFailureException {
        try {
            createWeaver().weaveAll();
        } catch (Exception e) {
            throw new MojoFailureException("failed", e);
        }
    }
}
