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
package org.apache.commons.weaver.lifecycle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines token classes corresponding to the elements of the {@link WeaveLifecycle}.
 *
 * @since 1.2
 */
public class WeaveLifecycleToken {
    /**
     * Declares the association between a {@link WeaveLifecycleToken} and an element of the {@link WeaveLifecycle}.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Represents {
        /**
         * The {@link WeaveLifecycle} stage represented by the annotated {@link WeaveLifecycleToken} type.
         * @return {@link WeaveLifecycle}
         */
        WeaveLifecycle value();
    }

    /**
     * Represents {@link WeaveLifecycle#CLEAN}.
     */
    @Represents(WeaveLifecycle.CLEAN)
    public static final class Clean extends WeaveLifecycleToken {
    }

    /**
     * Represents {@link WeaveLifecycle#WEAVE}.
     */
    @Represents(WeaveLifecycle.WEAVE)
    public static final class Weave extends WeaveLifecycleToken {
    }
}
