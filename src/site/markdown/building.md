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

Apache Maven 3 is required to build Apache Commons Weaver. Things to know:

### Self-referential multimodule build
Because of the various interdependencies among modules, certain goals of
certain Maven plugins will fail until the project has been added to your
local repository using `mvn install`, e.g.
`mvn dependency:list`, `mvn dependency:tree`, and possibly others.

### Site building issues
The Commons Weaver site runs out of permgen space (on applicable Java versions)
when built with default JVM settings; the `MAVEN_OPTS` environment variable can
be used to set `MaxPermSize`. `-XX:MaxPermSize=96m` seems to be adequate.

