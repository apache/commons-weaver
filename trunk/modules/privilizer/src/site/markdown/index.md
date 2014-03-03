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

## Apache Commons Weaver Privilizer

Provides machinery to automate the handling of Java Security access
controls in code.  This involves wrapping calls that may trigger
`java.lang.SecurityException`s in `PrivilegedAction` objects.
Unfortunately this is quite an expensive operation and slows code
down considerably; when executed in an environment that has no
`SecurityManager` activated it is an utter waste.
The typical pattern to cope with this is:

```java
if (System.getSecurityManager() != null) {
  AccessController.doPrivileged(new PrivilegedAction<Void>() {
    public Void run() {
      doSomethingThatRequiresPermissions();
      return null;
    }
  });
} else {
  doSomethingThatRequiresPermissions();
}
```

This becomes tedious in short order.  The immediate response of a
typical developer:  relegate the repetitive code to a set of
utility methods.  In the case of Java security, however, this
approach is considered risky.  The purpose of the Privilizer, then,
is to instrument compiled methods originally annotated with our
`@Privileged` annotation.  This annotation is retained in the
classfiles, but not available at runtime, and there are no runtime
dependencies.

### Basic Privilization

```java

@Privileged
private void doSomethingThatRequiresPermissions() {
  ...
}
```

Annotating a method with the [@Privileged][privileged] annotation will cause
the [PrivilizerWeaver][privilizerWeaver] to generate these checks automatically,
leaving you to simply implement the code!

### Blueprint Privilization
The so-called "blueprint" feature returns to the concept of static utility
methods.  Why are these considered a liability?  Because your trusted code
presumptuously extends your trust via public methods to any class in the JVM,
almost certainly contrary to the wishes of the owner of that JVM. Our
blueprint technique allows you to define (or reuse) static utility methods
in a secure way:  simply define these utility methods in a
`SecurityManager`-agnostic manner and let the consuming class request that
calls to them be treated as blueprints for `@Privileged` methods:

```java
public class Utils {
  public static void doSomethingThatRequiresPrivileges() {
    ...
  }
}

@Privilizing(CallTo(Utils.class))
public class UtilsClient {
  public void foo() {
    Utils.doSomethingThatRequiresPrivileges();
  }
}
```

The static methods of the `Utils` class will be called as though they had been
locally declared and annotated with `@Privileged`. See the documentation of the
[@Privilizing][privilizing] annotation for more information on how to specify
multiple classes, restrict to only certain methods, etc.

*Q:* What if my utility methods access static variables of their declaring class?

*A:* The imported methods reference those fields via reflection; i.e. the
    original fields are used.

*Q:* Does this modify the accessibility of those fields?

*A:* Yes, but only for the duration of the method implementation.  The fields'
    accessibility is checked before execution, and if a given field is not
    accessible on the way in, it will be restored to its original state in
    a `finally` block.

### Configuration
The `PrivilizerWeaver` supports the following options:

- `privilizer.accessLevel` : name of the highest [AccessLevel][accessLevel] to privilize (default `PRIVATE`)
- `privilizer.policy` : name of the [Policy][policy] (determines when to check for a `SecurityManager`)

[privileged]: ../../apidocs/org/apache/commons/weaver/privilizer/Privileged.html
[privilizerWeaver]: ../../apidocs/org/apache/commons/weaver/privilizer/PrivilizerWeaver.html
[privilizing]: ../../apidocs/org/apache/commons/weaver/privilizer/Privilizing.html
[policy]: ../../apidocs/org/apache/commons/weaver/privilizer/Policy.html
[accessLevel]: ../../apidocs/org/apache/commons/weaver/privilizer/AccessLevel.html
