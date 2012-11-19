# Commons Privilizer

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

### With Privilizer

```java

@Privileged
private void doSomethingThatRequiresPermissions() {
  ...
}
```

Commons Privilizer provides both a Maven plugin and an Antlib for
weaving your compiled, annotated classes. You can control the weaving
behavior by parameterizing the Maven goals and Ant task with
the [Policy][policy] and [AccessLevel][accessLevel] `enum`s.

[policy]: apidocs/org/apache/commons/privilizer/weave/Privilizer.Policy.html
[accessLevel]: apidocs/org/apache/commons/privilizer/weave/AccessLevel.html
