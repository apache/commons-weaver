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

Occasionally, as Java developers, we encounter a problem whose solution
simply cannot be expressed in the Java language. Often, the Java annotation
processing tools can be used to great effect, and they should not be
dismissed as your first line of defense when you need to generate additional
classes. Occasionally, however, our only recourse is to manipulate existing
class files. It is these situations which Apache Commons Weaver was designed
to address.

Apache Commons Weaver consists of:

- [Core Framework](#core)
- [Weaver Modules](#weavers)
- [Maven Plugin](#maven)
- [Antlib](#antlib)

The Maven Plugin and Antlib are used for invoking Weaving facilities. Below you will 
find a graph with a high level overview of Apache Commons Weaver project.

![Apache Commons Weaver project](images/weaver.png)

Latest API documentation is [here](apidocs/index.html).

### <a name="core"></a> Core Framework
The [Commons Weaver Processor](commons-weaver-parent/commons-weaver-processor/index.html)
defines a "weaver module" service provider interface (SPI) as well as
the facilities that use the Java `ServiceLoader` to discover and invoke
defined weaver modules for simple filesystem-based bytecode weaving.

### <a name="weavers"></a> Weaver Modules
A number of [Weaver Modules](commons-weaver-parent/commons-weaver-modules-parent/index.html)
are provided by the Commons Weaver project.
Typically a weaver module may respect a set of configuration
properties which should be documented along with that module.

### <a name="maven"></a> Maven Plugin
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

### <a name="antlib"></a> Antlib
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

## Custom Weaver Modules
As discussed, some modules are provided for common cases, and the developers
welcome suggestions for useful modules, but there is no reason not to get
started writing your own weaver module (assuming you are sure this is the right
solution, or just want to do this for fun) now! When the processor framework
invokes your custom `Weaver`, it will pass in a `Scanner` that can be used to
find the classes you are interested in. Request the original bytecode from the
`WeaveEnvironment` and make your changes (for this task you will save time and
frustration using one of the available open source Java bytecode manipulation
libraries). Save your changes back to the `WeaveEnvironment`. Rinse, repeat.
Hint: if your `Weaver` uses configuration parameters to dictate its
behavior, it can leave a scannable "footprint" in your woven classes. Then
implement the `Cleaner` SPI to find and delete these in the case that the
current configuration is incompatible with the results of an earlier "weaving."

## Examples
The canonical example is the [privilizer module](commons-weaver-parent/commons-weaver-modules-parent/commons-weaver-privilizer-parent/index.html).

A simple example could be exposing annotated methods for a REST API. Suppose 
you want to expose only classes annotated with @WebExposed to your Web REST API.

      package example;

      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;

      /**
       * Marks methods that interest our weaver module.
       */
      @Target(ElementType.METHOD)
      @Retention(RetentionPolicy.CLASS)
      public @interface WebExposed {

      }

And your POJO object annotated.

      package example;

      /**
       * Represents a user in our system.
       */
      public class User {
          
          private String name;
          private String surname;
          private Integer age;

          public User() {
              super();
          }

          public User(String name, String surname, Integer age) {
              super();
              this.name = name;
              this.surname = surname;
              this.age = age;
          }

          @WebExposed
          public String getName() {
              return name;
          }

          public void setName(String name) {
              this.name = name;
          }

          @WebExposed
          public String getSurname() {
              return surname;
          }

          public void setSurname(String surname) {
              this.surname = surname;
          }

      }


Now in order to scan your classpath and find the annotated methods normally you would 
use Java Reflection API or something similar, but the good news is that Apache 
Commons Weaver abstracts this for you. 

      package example;

      import java.io.File;
      import java.lang.annotation.ElementType;
      import java.util.Arrays;
      import java.util.Properties;

      import org.apache.commons.weaver.WeaveProcessor;
      import org.apache.commons.weaver.model.AnnotatedElements;
      import org.apache.commons.weaver.model.ScanRequest;
      import org.apache.commons.weaver.model.ScanResult;
      import org.apache.commons.weaver.model.Scanner;
      import org.apache.commons.weaver.model.WeavableMethod;
      import org.apache.commons.weaver.model.WeaveEnvironment;
      import org.apache.commons.weaver.model.WeaveInterest;
      import org.apache.commons.weaver.spi.Weaver;

      public class MyWeaver implements Weaver {

          @Override
          public boolean process(WeaveEnvironment environment, Scanner scanner) {
              // We want to find methods annotated with @WebExposed.
              WeaveInterest findAnnotation = WeaveInterest.of(WebExposed.class, ElementType.METHOD);
              ScanResult scanResult = scanner.scan(new ScanRequest().add(findAnnotation));
              AnnotatedElements<WeavableMethod<?>> annotatedMethods = scanResult.getMethods();
              for (WeavableMethod<?> method : annotatedMethods) {
                  // The API code is out of the scope of this guide, but you can do other things here, 
                  // like modifying your class
                  System.out.println("Expose method " + method.getTarget().getName() + " in our REST API");
              }
              return true;
          }
          
      }

Before running the example above you need to tell the ServiceProvider about 
your custom Weaver. This is done by adding a file to your _META-INF_ directory. 
If you are using Maven, then creating <code>src/main/resources/META-INF/services/org.apache.commons.weaver.spi.Weaver</code> 
with <pre>example.MyWeaver</pre> will instruct ServiceLoader to load your Weaver class.

##FAQ

* *Q*: Why not just use [AspectJ](http://eclipse.org/aspectj/)?

    *A*: The original motivation to develop the codebase that evolved into
         Commons Weaver instead of simply using AspectJ was to avoid the
         runtime dependency, however small, introduced by the use of AspectJ.
         Additionally, later versions of AspectJ are licensed under the
         [EPL](http://eclipse.org/legal/epl-v10.html) which can be
         considered less permissive than the Apache license. Choice is
         A Good Thing.
* *Q*: What is the relationship between Commons Weaver and Commons BCEL/ASM/Javassist/CGLIB?

    *A*: Rather than being an _alternative_ to these technologies, Commons
         Weaver can be thought of as providing a structured environment in
         which these technologies can be put to use. I.e., the bytecode
         modifications made by a given `Weaver` implementation would typically
         be implemented using one of these (or comparable) libraries.

[mvnplugin]: commons-weaver-parent/commons-weaver-maven-plugin/plugin-info.html
[antlib]: commons-weaver-parent/commons-weaver-antlib/index.html
[antxt]: http://ant.apache.org/manual/using.html#external-tasks
[antref]: http://ant.apache.org/manual/using.html#references
