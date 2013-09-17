<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Commons Weaver

Provides a general framework for the application of transformations
to compiled Java bytecode. Commons Weaver provides:

* [Core Framework](commons-weaver/index.html)
* [Weaver Modules](commons-weaver-modules-parent/index.html)
* [Maven Plugin] (commons-weaver-maven-plugin/plugin-info.html)
* [Antlib] (commons-weaver-antlib-parent/commons-weaver-antlib/index.html)

##FAQ

* *Q*: Why not just use [AspectJ](http://eclipse.org/aspectj/)?

    *A*: The original motivation to develop the codebase that evolved into
         Commons Weaver instead of simply using AspectJ was to avoid the
         runtime dependency, however small, introduced by the use of AspectJ.
         Additionally, later versions of AspectJ are licensed under the
         [EPL](http://eclipse.org/legal/epl-v10.html) which can be
         considered less permissive than the Apache license. Choice is
         A Good Thing.
