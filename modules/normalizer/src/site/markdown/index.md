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

## Apache Commons Weaver Normalizer

The Normalizer module merges identical anonymous class definitions into
a single type, thereby "normalizing" them and reducing their
collective footprint on your archive and more importantly on your JVM.

Considers only the simplest case in which:

 - no methods are implemented

 - the constructor only calls the super constructor

An anonymous class which violates these restrictions will be considered
too complex and skipped in the interest of correctness.


### Configuration
The [NormalizerWeaver][normalizerWeaver] supports the following options:

- `normalizer.superTypes` : comma-delimited list of types whose
 subclasses/implementations should be normalized, e.g.
 `javax.enterprise.util.TypeLiteral`.

- `normalizer.targetPackage` : package to which merged types should be added.


[normalizerWeaver]: ../../../apidocs/org/apache/commons/weaver/normalizer/NormalizerWeaver.html
