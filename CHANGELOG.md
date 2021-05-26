Changelog
=========

v1.1.0
------
* Default class name when given a lookup must be in the same package.
* Strict check when adding constructor with a MethodType.
* Allow methods defined in Object to be invoked on interface instances.
* Support adding annotations.
* Switch statements with one case are converted to if statements.
* Add a convenience exception catching method.
* Support referencing the class being made as an array.
* Generated classes are loaded into separate ClassLoaders, keyed by package name, to facilitate
  class unloading. To be unloaded, all classes generated in that package must be unreachable.

v1.0.2 (2021-05-05)
------
* Requires Java 12, in order to be buildable by automated systems. The java.lang.constant
  package isn't available in Java 11.

v1.0.0 (2021-05-05)
------

* First released version.
