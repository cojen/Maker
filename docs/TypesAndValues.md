The Cojen/Maker library supports many different kinds of data types and values. To keep the API simple, types and values are passed as any kind of `Object`, but only a subset is allowed.

Types
-----

The following kinds of types are supported:

- Class &mdash; Examples: `int.class`, `String.class`, `int[].class`, etc.
- String &mdash; Fully qualified class name or descriptor: `"int"`, `"java.lang.String"`, `"int[]"`, `"I"`, `"Ljava/lang/String;"`, `"[I"`, etc.
- ClassMaker &mdash; Specifies the class being made.
- Variable or Field &mdash; Specifies the type used by the given `Variable` or `Field`.
- null &mdash; Specifies the `null` type or a context specific default such as `void.class`.
- ClassDesc &mdash; Specifies a type descriptor.

When making a factory method that constructs the class being made, pass the current `ClassMaker`. Unless explicitly specified, the actual name of the class being made isn't known until it's finished.

```java
ClassMaker cm = ...
MethodMaker factory = ...

var instance = factory.new_(cm, ...);
...
factory.return_(instance)
```

Variables can be used as generic type carriers, and this doesn't actually allocate a variable slot.

```java
MethodMaker mm = ...
var builderType = mm.var(StringBuilder.class);
var b1 = mm.new_(builderType, ...);
var b2 = mm.new_(builderType, ...);
...
```

Values
------

A value can be a `Variable`, a `Field` or a constant:

- Primitive type &mdash; Examples: `123`, `true`, etc.
- Boxed primitives &mdash; `Integer`, `Boolean`, etc.
- String
- Class
- Enum
- MethodType
- MethodHandleInfo
- ConstantDesc
- Constable

Constants of type `MethodHandleInfo` are treated specially when assigning them to variable or parameters of type `MethodHandle`. A lookup is performed at runtime which resolves the `MethodHandle` instance. Handling of `ConstantDesc` and `Constable` is also treated specially. The actual type is determined by the resolved constant.

Constants that aren't in the above set can be specified via `Variable.setConstant` or `Variable.condy`. The `setConstant` method supports any kind of object, but this feature only works for classes which are directly made. If the class is written to a file and then loaded from it, the constant won't be found, resulting in a linkage error.

Value type conversions
----------------------

Automatic value type conversions are performed when setting variables and invoking methods:

- Widening &mdash; Example: `int` to `long`
- Boxing &mdash; Example: `int` to `Integer`
- Widening and boxing &mdash; Example: `int` to `Long`, `Number`, or `Object`
- Reboxing and widening &mdash; Example: `Integer` to `Long`
- Unboxing &mdash; Example: `Integer` to `int` (NullPointerException is possible)
- Unboxing and widening &mdash; Example: `Integer` to `long` (NullPointerException is possible)


