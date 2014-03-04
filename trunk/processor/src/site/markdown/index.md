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

## Apache Commons Weaver Processor

This module provides the `org.apache.commons:commons-weaver` artifact.
It defines the Apache Commons Weaver SPI as well as the basic build-time
(filesystem-based) processors that detect, configure, and invoke available
modules.

### WeaveProcessor
The [WeaveProcessor][wp] invokes available implementations of the
[Weaver][weaver] SPI.

### CleanProcessor
The [CleanProcessor][cp] invokes available implementations of the
[Cleaner][cleaner] SPI.

[cp]: apidocs/org/apache/commons/weaver/CleanProcessor.html
[wp]: apidocs/org/apache/commons/weaver/WeaveProcessor.html
[cleaner]: apidocs/org/apache/commons/weaver/spi/Cleaner.html
[weaver]: apidocs/org/apache/commons/weaver/spi/Weaver.html
