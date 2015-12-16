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
package org.apache.commons.weaver.spi;

import org.apache.commons.weaver.lifecycle.WeaveLifecycle;
import org.apache.commons.weaver.lifecycle.WeaveLifecycleToken.Weave;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeaveEnvironment;

/**
 * A {@link Weaver} implementation implements the {@code WEAVE} stage of the {@link WeaveLifecycle} by performing the
 * byte code enhancement in the classes.
 */
public interface Weaver extends WeaveLifecycleProvider<Weave> {
    /**
     * Using the supplied {@link Scanner}, process a {@link WeaveEnvironment}.
     *
     * @param environment to use
     * @param scanner to use
     * @return whether any work was done.
     */
    boolean process(WeaveEnvironment environment, Scanner scanner);
}
