# Commons Weaver

Provides a general framework for the application of transformations
to compiled Java bytecode. Commons Weaver provides:

* [Core Framework](commons-weaver/index.html)
* [Weaver Modules](commons-weaver-modules-parent/index.html)
* [Maven Plugin] (commons-weaver-maven-plugin/index.html)
* [Antlib] (commons-weaver-antlib-parent/index.html)

##FAQ

* *Q*: Why not just use AspectJ?

    *A*: The original motivation to develop the codebase that evolved into
         Commons Weaver instead of simply using AspectJ was to avoid the
         runtime dependency, however small, introduced by the use of AspectJ.
