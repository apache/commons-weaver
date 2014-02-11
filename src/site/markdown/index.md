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

# Apache Commons Weaver

Provides a general framework for the application of
transformations to compiled Java bytecode. It consists of:

## Core Framework
The [Commons Weaver Core](commons-weaver/index.html)
defines a "weaver module" service provider interface (SPI) as well as
the facilities that use the Java `ServiceLoader` to discover and invoke
defined weaver modules for simple filesystem-based bytecode weaving.

## Weaver Modules
A number of [Weaver Modules](commons-weaver-modules-parent/index.html)
are provided by the Commons Weaver project.
Typically a weaver module may respect a set of configuration
properties which should be documented along with that module.

### What can these do for me?
The canonical example is the [privilizer module](commons-weaver-modules-parent/commons-weaver-privilizer-parent/index.html).

## Integration
The weaver module(s) applicable to your codebase should be available
on the classpath of whatever Java-based processing mechanism you select.
Your responsibilities are to:

 - trigger weave processing in some way
 - make the desired weaver module(s) available for processing
 - (optionally) provide configuration properties for applicable modules

There are two provided mechanisms for invoking Weaving facilities:

### Maven Plugin
The [Commons Weaver plugin for Apache Maven][mvnplugin] aims to integrate
Weaver as smoothly as possible for Maven users. Here is an example
of configuring the `privilizer` module:

      <plugin>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-weaver-maven-plugin</artifactId>
        <version>${commons.weaver.version}</version>
        <configuration>
          <weaverConfig>
            <privilizer.accessLevel>${privilizer.accessLevel}</privilizer.accessLevel>
            <privilizer.policy>${privilizer.policy}</privilizer.policy>
            <privilizer.verify>${privilizer.verify}</privilizer.verify>
          </weaverConfig>
        </configuration>
        <executions>
          <execution>
            <goals>
              <goal>prepare</goal>
              <goal>weave</goal>
            </goals>
          </execution>
        </executions>
        <dependencies>
          <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-weaver-privilizer-api</artifactId>
            <version>${commons.weaver.version}</version>
          </dependency>
          <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-weaver-privilizer</artifactId>
            <version>${commons.weaver.version}</version>
          </dependency>
        </dependencies>
      </plugin>

### Antlib
The [Commons Weaver Antlib][antlib] provides tasks and types to
facilitate the integration of Commons Weaver into your Apache Ant-based
build process. Here the user will make the `commons-weaver-antlib` `jar`
(which includes the Apache Commons Weaver processor and its dependencies),
along with the `jar` files of the desired modules, available to
the Ant build using one of the various mechanisms supported. More
information on this is available [here][antxt]. Having done this the
basic approach will be to parameterize one of the provided tasks
(`clean`|`weave`) with a `settings` element. If both `weave` and `clean`
tasks are used, defining a [reference][antref] to the `settings` object
and referencing it using the `settingsref` attribute is recommended, as
seen here:

      <settings id="weavesettings"
                target="target/classes"
                classpathref="maincp">
        <properties>
          <privilizer.accessLevel>${privilizer.accessLevel}</privilizer.accessLevel>
          <privilizer.policy>${privilizer.policy}</privilizer.policy>
          <privilizer.verify>${privilizer.verify}</privilizer.verify>
        </properties>
      </settings>

      <clean settingsref="weavesettings" />
      <weave settingsref="weavesettings" />

Multiple weaving targets (e.g. `main` vs. `test`) are of course woven
using different `settings`.

##FAQ

* *Q*: Why not just use [AspectJ](http://eclipse.org/aspectj/)?

    *A*: The original motivation to develop the codebase that evolved into
         Commons Weaver instead of simply using AspectJ was to avoid the
         runtime dependency, however small, introduced by the use of AspectJ.
         Additionally, later versions of AspectJ are licensed under the
         [EPL](http://eclipse.org/legal/epl-v10.html) which can be
         considered less permissive than the Apache license. Choice is
         A Good Thing.

[mvnplugin]: commons-weaver-maven-plugin/plugin-info.html
[antlib]: commons-weaver-antlib-parent/commons-weaver-antlib/index.html
[antxt]: http://ant.apache.org/manual/using.html#external-tasks
[antref]: http://ant.apache.org/manual/using.html#references
