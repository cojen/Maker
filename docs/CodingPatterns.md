This page shows how some common coding patterns can be implemented using the Cojen/Maker library.

If statements
=============

```java
if (a < b) {
    <code>
}
```

The key here is to flip the `if` condition:

```java
MethodMethod mm = ...
Variable a, b = ...

Label else_ = mm.label();
a.ifGe(b, else_); // flipped from < to >=
<code>
else_.here();
```

If-else statement
-----------------

```java
if (a < b) {
    <code>
} else {
    <more code>
}
```

The flipped `if` condition pattern can be used here too:

```java
MethodMethod mm = ...
Variable a, b = ...

Label else_ = mm.label();
Label cont = mm.label();
a.ifGe(b, else_); // flipped from < to >=
<code>
mm.goto_(cont);
else_.here();
<more code>
cont.here();
```

The original `if` condition can remain the same, but then the code sections must be flipped:

```java
Label then = mm.label();
Label cont = mm.label();
a.ifLt(b, then);
<more code>
mm.goto_(cont);
then.here();
<code>
cont.here();
```

Loops
=====

Loops always follow the same basic pattern.

```java
while (true) {
    <code>
    if (a < b) break;
    <more code>
}
```

Note that the `if` condition doesn't need to be flipped to break out of the loop:

```java
MethodMethod mm = ...
Variable a, b = ...

Label start = mm.label().here();
Label end = mm.label();
<code>
a.ifLt(b, end);
<more code>
mm.goto_(start);
end.here();
```

Conditional while loop
----------------------

```java
while (a < b) {
    <code>
}
```

This is similar to the simple loop, but with a flipped condition and one code section:


```java
MethodMethod mm = ...
Variable a, b = ...

Label start = mm.label().here();
Label end = mm.label();
a.ifGe(b, end); // flipped from < to >=
<code>
mm.goto_(start);
end.here();
```

Do-while loop
-------------

```java
do {
    <code>
} while (a < b);
```

This is the simplest loop to translate. There's only one label, and the condition isn't flipped:

```java
MethodMethod mm = ...
Variable a, b = ...

Label start = mm.label().here();
<code>
a.ifLt(b, start);
```

For loop
--------
```java
for (int i = 0; i < 10; i++) {
    <code>
    if (a < b) continue;
    <more code>
}
```

This is first logically translated to this:

```java
int i = 0;
while (i < 10) {
    <code>
    if (a >= b) {
        <more code>
    }
    i++;
}
```

And then it can be translated using the usual if and loop patterns:

```java
MethodMethod mm = ...
Variable a, b = ...

Variable i = mm.var(int.class).set(0);
Label start = mm.label().here();
Label end = mm.label();
Label cont = mm.label();
i.ifGe(10, end); // flipped from < to >=
<code>
a.ifLt(b, cont); // flipped back to original condition
<more code>
cont.here();
i.inc(1);
mm.goto_(start);
end.here();
```

Exception handling
==================

```java
try {
    <code>
} catch (Exception e) {
    <handler code>
}
```

When implementing a `catch` block, it's important that the handled code doesn't flow into the handler.

```java
MethodMaker mm = ...
Label tryStart = mm.label().here();
Label cont = mm.label();
<code>
mm.goto(cont); // branch past the handler
Label tryEnd = mm.label().here();
var e = mm.catch_(tryStart, tryEnd, Exception.class);
<handler code>
cont.here();
```

Try-finally
-----------

```java
lock.lock();
try {
    <code>
    if (a < b) return;
    <more code>
} finally {
    lock.unlock();
}
```

Implementing a `finally` statement is tricky, because every path which can exit the `try` block must be handled.

```java
MethodMaker mm = ...
Variable lock, a, b = ...

lock.invoke("lock");
Label tryStart = mm.label().here();
<code>
Label cont = mm.label();
a.ifGe(b, cont);
lock.invoke("unlock"); // unlock at this exit point
mm.return_();
cont.here();
<more code>
Label cont2 = mm.label();
mm.goto_(cont2);
Label tryEnd = mm.label().here();
var e = mm.catch_(tryStart, tryEnd, null); // catch anything
lock.invoke("unlock"); // unlock at this exit point
e.throw_();
cont2.here();
lock.invoke("unlock"); // unlock at this exit point
```

Note that this example is only safe if the `unlock` method is guaranteed to never throw an exception. Otherwise, two catch locations must be defined which can branch to the same handler. A built-in utility which generates the correct try-finally coding patterns would be nice to have. 
