The Cojen/Maker library is a dynamic Java class generator which is primarily focused on ease of use. Other popular class generation libraries expose the low-level details of the JVM architecture, but this knowledge isn't a prerequisite when using the the Cojen/Maker library.

Here's a simple "hello, world" example:

```java
ClassMaker cm = ClassMaker.begin().public_();

// public static void run()...
MethodMaker mm = cm.addMethod(null, "run").public_().static_();

// System.out.println(...
mm.var(System.class).field("out").invoke("println", "hello, world");

Class<?> clazz = cm.finish();
clazz.getMethod("run").invoke(null);
```

- [Types and values](docs/TypesAndValues.md)
- [Coding patterns](docs/CodingPatterns.md)
- [Examples](example/main/java/org/cojen/example)

A key feature of this library is that the JVM operand stack isn't directly accessible. Local variables are used exclusively, and conversion to the stack-based representation is automatic. In some cases this can result in suboptimal bytecode, but this only affects performance when the code is interpreted. Modern JVMs perform liveness analysis when generating machine code, and this eliminates the need to carefully utilize the operand stack.

In addition to simplying basic class generation, the features of the `java.lang.invoke` package are fully integrated, hiding most of the complexity. The `ObjectMethods` example shows how to define a bootstrap method which generates code "just in time".
