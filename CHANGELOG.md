Changelog
=========

v1.2.2
------
* Allow hidden classes to be unloaded when created without an explicit lookup object.

v1.2.1 (2021-08-21)
------
* Allow hidden class to made without an explicit lookup object provided.
* Preserve raw floating point constants (non-canonical NaNs).

v1.2.0 (2021-08-07)
------
* Add basic enum support.

v1.1.4 (2021-07-24)
------
* Fix race conditions when requesting constants defined by setExact.

v1.1.3 (2021-07-10)
------
* Allow relational comparisons against booleans, which are really just 0 and 1.

v1.1.2 (2021-06-19)
------
* Fixed VerifyError when converting a boxed primitive type and storing to an instance field.

v1.1.1 (2021-06-12)
------
* Fixed VerifyError when performing a logical operation against a boxed type.
* Fixed VerifyError where a local variable store was erroneously replaced with a pop instruction.

v1.1.0 (2021-06-04)
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
* Remove ProtectionDomain support. The SecurityManager is being deprecated, and the concept of
  a CodeSource makes little sense with dynamically generated code.
* Provide access to the class loader before the class is finished.
* Support distinct class loaders for generated classes which shared a common parent loader.
* Add a method to finish a class into a full privilege access lookup object.
* Remove strictfp support.

v1.0.2 (2021-05-05)
------
* Requires Java 12, in order to be buildable by automated systems. The java.lang.constant
  package isn't available in Java 11.

v1.0.0 (2021-05-05)
------

* First released version.
