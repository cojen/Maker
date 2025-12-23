Changelog
=========

v2.7.1 (2025-12-23)
------
* Support invocation of inherited interface methods, for resolving default method conflicts.

v2.7.0 (2025-05-23)
------
* Overload the addMethod and addConstructor methods to accept a MethodTypeDesc.
* Added support for indicating that parameters can be final, synthetic, or mandated.

v2.6.5 (2025-03-23)
------
* Fields and methods should be referenced by the class they're accessed from, and not the class
  they're defined in. This change affects linkage rules for classes which aren't immediately
  loaded, and it's now consistent with the Java compiler.

v2.6.4 (2025-02-23)
------
* Removed the condy workaround code. If using the condy feature, be sure use JDK 19 or above to
  ensure that HotSpot fully compiles code which uses it. See bug: JDK-8270928

v2.6.3 (2025-01-23)
------
* Variables which are used once are never eliminated when they have a name.
* Reduce the number of local variables by using a swap against pushes of constants and static
  fields.

v2.6.2 (2024-12-23)
------
* Fixed how the debug option allows broken class files to be written.
* Reduce the number of local variables by using a swap in even more cases, not just stores into
  instance fields.
* Minor performance optimizations.

v2.6.1 (2024-11-23)
------
* Added methods to obtain the return type and parameter types of a method being made.
* Added a method to make a class into a Lookup with an explicit name.
* Reduce the number of local variables used when performing basic stores into instance fields.

v2.6.0 (2024-10-23)
------
* Added a Type interface for explicitly representing types.
* Added support for type use annotations. They're not supported when the target is in the body
  of a method, because they're not accessible via reflection anyhow. They're not supported for
  generic types either, because generic types aren't currently supported at all.

v2.5.11 (2024-09-07)
-------
* Added a variable decrement method.
* Avoid generating a conversion instruction when comparing against a constant primitive value.
* Reduce constant pool pollution when the StackMapTable attribute isn't needed.

v2.5.10 (2024-08-07)
-------
* Added a convenience method for initializing static fields to complex constants.

v2.5.9 (2024-07-07)
------
* Stricter conversion checks when setting a primitive variable to a constant.
* Hidden classes aren't renamed when finished anymore, because it would leave a lingering
  useless constant pool entry.

v2.5.8 (2024-06-07)
------
* When the debug option is enabled, the list of unimplemented methods is also printed.
* When the debug option is enabled and a variable is accessed without having been assigned, an
  IllegalStateException is logged, allowing the broken class file to be written such that it
  can be examined in detail.

v2.5.7 (2024-05-07)
------
* Detect simple cases when a variable is accessed without having been assigned, preventing a
  VerifyError. Instead, an IllegalStateException is thrown when the class is finished.
* Added more methods for working with boxed primitive types.

v2.5.6 (2024-04-16)
------
* Fixed a bug when calling ClassMaker.finishTo which could write corrupt UTF-8 strings which
  had any non-ASCII characters.

v2.5.5 (2024-03-01)
------
* Added a feature to directly install classes into the class loader used by generated classes.
* Added a method to obtain the ClassMaker name.
* Improved exception messages when a method cannot be finished.

v2.5.4 (2024-01-24)
------
* Switching on a string field now acts upon a stable local variable copy.
* Added a method to insert code after a label.
* Added switch methods which support enum and object cases.

v2.5.3 (2023-12-12)
------
* Added convenience methods for generating conditional logic.
* Added a method which can check if the class being made has any unimplemented abstract methods.
* Disallow null types, which never worked anyhow.

v2.5.2 (2023-11-27)
------
* Fixed hidden method invocation against inherited methods.

v2.5.1 (2023-10-31)
------
* Fixed a bug in which static fields sometimes behaved as if they were instance fields. This
  only affected field accesses which indirectly used a VarHandle.
* Fixed a bug in which local variables used as pseudo field coordinates might be unavailable
  for future use.
* Allow direct access to the fields, constructors, and methods of hidden classes.
* Allow hidden classes to work with the cast and instanceOf methods.
* Allow exact constant slots to be shared, which can help eliminate duplicated dynamic
  constants.

v2.5.0 (2023-07-29)
------
* Depends on Java 17 (was Java 12 previously).
* Hidden classes which have exact constants are no longer prevented from being unloaded.

v2.4.8 (2023-06-28)
------
* Added a switch method which supports string cases.
* Use Boolean.TRUE/FALSE when setting an object variable to a boolean constant instead of
  calling Boolean.valueOf(true/false).

v2.4.7 (2023-03-04)
------
* Fixed a bug in the override method which failed to look for super class interfaces.
* Added methods to obtain the FieldMaker name, the MethodMaker name, and the MethodMaker
  parameter count.
* Restore discarding of references to exact constants when they're accessed by a class
  initializer. This is safe because class initialization runs at most once.

v2.4.6 (2023-02-18)
------
* Prevent class loading deadlock when a class in one group needs to load a class in another group.

v2.4.5 (2023-02-04)
------
* Never discard references to exact constants until the owning class is unloaded. This
  prevents race conditions when multiple threads attempt to resolve the constant at the same
  time. This behavior is also required for supporting class redefinition.
* Support catching multiple exception types in the same handler.

v2.4.4 (2022-10-31)
------
* Support defining records.
* FieldMaker can now be used as a type specifier.

v2.4.3 (2022-10-03)
------
* Fixed a bug which could eliminate labels used by exception handlers, causing a ClassFormatError.

v2.4.2 (2022-08-30)
------
* Fixed detection of code flowing through the end of a method when the code at the end is dead.
* Type information from a freshly made class is now still available after the class is finished.

v2.4.1 (2022-07-24)
------
* Fixed a bug which caused loaded classes to sometimes get lost.
* Don't pollute the ClassLoader lock table with lookup class names that will never be removed.
* If available, use a virtual thread to clean up cache entries.

v2.4.0 (2022-07-10)
------
* Fix concat method when given more than 100 arguments, and some of them are double or long
  variables.
* Change the format of the addAttribute method in order to support more kinds of JVM attributes.
* Support generic type signatures.
* Support named method parameters.
* Support parameter annotations.
* Support defining a module-info class.
* Support sealed classes.
* Support defining annotations.

v2.3.0 (2022-06-12)
------
* Fix a potential race condition which can cause the ClassLoader to change.
* Fix a bug which ignored clinit exception handlers for all but the first one.
* Define a new method for creating explicitly named classes.
* Add a convenience method which checks if an added method overrides an inherited one.

v2.2.3 (2022-05-15)
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
