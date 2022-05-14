Changelog
=========

v2.2.3
------
* Fix a ConcurrentModificationException when comparing against a dynamic constant.
* Detect simple cases in which the super or this constructor isn't invoked properly.

v2.2.2 (2022-05-01)
------
* Fix support for 'and', 'or' and 'xor' operations against booleans, which were needlessly
  disallowed by version 1.3.3.
* Fix signature polymorphic invocation when a null parameter is provided.
* Don't use signature polymorphic invocation for the MethodHandle.invokeWithArguments method.

v2.2.1 (2022-04-10)
------
* Fix calculation of invokeinterface nargs operand when passing long or double arguments.
* Throw an exception when attempting to make a method be static after the parameters have been
  accessed, preventing a confusing VerifyError later.

v2.2.0 (2022-03-01)
------
* Add a convenience synchronized_ method.
* Add methods to Variable and Label to obtain the MethodMaker they belong to.
* Check for string constants which are too large for the modified UTF-8 form.
* Disable a workaround for a dynamic constant bug when using Java 19.

v2.1.0 (2022-01-27)
------
* Support adding simple generic JVM attributes.
* Add a convenience goto_ method.

v2.0.3 (2022-01-02)
------
* Keep references to complex bootstrap constants in order to support class redefinition, as
  required by profilers and other instrumentation agents.

v2.0.2 (2021-12-12)
------
* Fix interface method invocation when passing long or double arguments.

v2.0.1 (2021-11-29)
------
* Fix type inference for a null parameter passed to an indy method.
* Major version bump is required to fix an incorrect earlier tag (1.33 instead of 1.3.3).

v1.3.3 (2021-11-01)
------
* Fix ClassLoader deadlock.
* Math and logical operations against types smaller than an int must not expose more precision
  than the type allows. Also, math and logical operations against boolean is now disallowed.

v1.3.2 (2021-10-18)
------
* Detect if start label is unpositioned when calling the `catch_` method.

v1.3.1 (2021-10-03)
------
* Fix invocation of overloaded methods which accept varargs.

v1.3.0 (2021-09-21)
------
* Add a convenience method to clear a variable.

v1.2.2 (2021-09-07)
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
