The Cojen/Maker library is a dynamic Java class generator which is primarily focused on ease of use. Other popular class generation libraries expose the low-level details of the JVM architecture, but this knowledge isn't a prerequisite when using the the Cojen/Maker library. The trade-off is somewhat slower performance when generating bytecode, but the performance of the resulting code is identical when running with a modern JVM.

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

