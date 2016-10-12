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
import org.apache.commons.weaver.lifecycle.WeaveLifecycleToken.Clean;
import org.apache.commons.weaver.model.Scanner;
import org.apache.commons.weaver.model.WeaveEnvironment;

/**
 * SPI to provide a means for a weaver module to remove woven classes during incremental builds, if necessary.
 * Implements the {@code CLEAN} stage of the {@link WeaveLifecycle}.
 */
public interface Cleaner extends WeaveLifecycleProvider<Clean> {
    /**
     * Using the supplied {@link Scanner}, clean a {@link WeaveEnvironment}.
     *
     * @param environment to use
     * @param scanner to use
     * @return whether any work was done.
     */
    boolean clean(WeaveEnvironment environment, Scanner scanner);
}
