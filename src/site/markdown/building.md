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

Commons Weaver is built using Maven 3 in typical fashion.  Things to know:

### Testing with security enabled
The Privilizer is the fundamental "guinea pig" weaver module.
Since the whole point of the Privilizer relates to Java security, it is only
natural that its tests be executable with Java security enabled. It is also
reasonable to test without security enabled, to show that your code works as
always.  The `example` and `ant/test` modules each have a `sec` profile defined;
You can run their tests with this profile enabled to turn on Java security.

### Antlib Test module
Located at `ant/test`, this module\'s tests are implemented by unpacking the
source of the `example` module and reusing it.  For this reason, the
`example` module must have been packaged previously to executing the `ant/test`
tests, so in a multimodule build you should at least specify the `package`
phase of the default lifecycle.  Alternatively, you can disable this module\'s
tests by deactivating the profile in which they are set up: `antlib-test`.

Similarly, when building the project site you should deactivate the
`antlib-test` profile, to stop this module's tests from requiring the
`example` module to have been previously packaged. 
