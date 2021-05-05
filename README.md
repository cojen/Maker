[![](https://jitpack.io/v/cojen/Maker.svg)](https://jitpack.io/#cojen/Maker)

The Cojen/Maker module is a low-level dynamic Java class generator which is designed for ease of use.

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

- [Javadocs](https://cojen.github.io/Maker/javadoc/org.cojen.maker/org/cojen/maker/package-summary.html)
- [Coding patterns](docs/CodingPatterns.md)
- [Examples](example/main/java/org/cojen/example)

A key feature of this module is that the JVM operand stack isn't directly accessible. Local variables are used exclusively, and conversion to the stack-based representation is automatic. In some cases this can result in suboptimal bytecode, but this only affects performance when the code is interpreted. Modern JVMs perform liveness analysis when generating machine code, and this eliminates the need to carefully utilize the operand stack.

In addition to simplying basic class generation, the features of the `java.lang.invoke` package are fully integrated, hiding most of the complexity. The `ObjectMethods` example shows how to define a bootstrap method which generates code "just in time".
