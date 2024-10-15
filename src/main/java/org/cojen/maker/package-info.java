/*
 *  Copyright 2020 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * Dynamic Java class file generator. Here's a simple "hello, world" example:
 *
 * {@snippet lang="java" :
 * ClassMaker cm = ClassMaker.begin().public_();
 *
 * // public static void run()...
 * MethodMaker mm = cm.addMethod(null, "run").public_().static_();
 *
 * // System.out.println(...
 * mm.var(System.class).field("out").invoke("println", "hello, world");
 *
 * Class<?> clazz = cm.finish();
 * clazz.getMethod("run").invoke(null);
 * }
 *
 * <h2>Types and Values</h2>
 *
 * The API supports many kinds of data types and values. To keep things simple, types
 * and values are passed as any kind of {@code Object}, but only a subset is allowed.
 *
 * <h3>Types</h3>
 *
 * The following kinds of types are supported:
 *
 * <ul>
 * <li>{@link Type} — An explicit type object.
 * <li>{@link java.lang.Class Class} — Examples: {@code int.class}, {@code String.class}, {@code int[].class}, etc.
 * <li>{@link java.lang.String String} — Fully qualified class name or descriptor: {@code "int"}, {@code "java.lang.String"}, {@code "int[]"}, {@code "I"}, {@code "Ljava/lang/String;"}, {@code "[I"}, etc.
 * <li>{@link ClassMaker} — Specifies the class being made.
 * <li>{@link Variable}, {@link Field}, or {@link FieldMaker} — Specifies the type used by the given {@code Variable} or {@code Field}.
 * <li>{@code null} — Specifies a context specific default such as {@code void.class}.
 * <li>{@link java.lang.constant.ClassDesc ClassDesc} — Specifies a type descriptor.
 * </ul>
 *
 * When making a factory method that constructs the class being made, pass the current {@code
 * ClassMaker}. Unless explicitly specified, the actual name of the class being made isn't
 * known until it's finished.
 *
 * {@snippet lang="java" :
 * ClassMaker cm = ...
 * MethodMaker factory = ...
 *
 * // Pass the ClassMaker as the type to instantiate.
 * var instance = factory.new_(cm, ...);
 * ...
 * factory.return_(instance)
 * }
 *
 * A {@code Variable} can be used as a generic type carrier, and this won't actually allocate a variable slot.
 *
 * {@snippet lang="java" :
 * MethodMaker mm = ...
 * var builderType = mm.var(StringBuilder.class);
 * var b1 = mm.new_(builderType, ...);
 * var b2 = mm.new_(builderType, ...);
 * ...
 * }
 *
 * <h3>Values</h3>
 *
 * A value can be a {@link Variable}, a {@link Field} or a constant:
 *
 * <ul>
 * <li>Primitive constant — Examples: {@code 123}, {@code true}, etc.
 * <li>Boxed constant — {@code Integer}, {@code Boolean}, etc.
 * <li>{@link java.lang.String String} constant
 * <li>{@link java.lang.Class Class} constant
 * <li>{@link java.lang.Enum Enum} constant
 * <li>{@link java.lang.invoke.MethodType MethodType} constant
 * <li>{@link java.lang.invoke.MethodHandleInfo MethodHandleInfo} constant
 * <li>{@link java.lang.constant.ConstantDesc ConstantDesc} constant
 * <li>{@link java.lang.constant.Constable Constable} constant
 * </ul>
 *
 * Constants of type {@code MethodHandleInfo} are treated specially when assigning them to
 * variables or parameters of type {@code MethodHandle}. A lookup is performed at runtime which
 * resolves the MethodHandle instance. Handling of {@code ConstantDesc} and {@code Constable}
 * is also treated specially — the actual type is determined by the resolved constant.
 *
 * <p>Constants that aren't in the above set can be specified via {@link Variable#setExact
 * Variable.setExact} or {@link Variable#condy Variable.condy}. The {@code setExact} method
 * supports any kind of object, but this feature only works for classes which are directly
 * {@link ClassMaker#finish finished}. If the class is written to a file and then loaded from
 * it, the constant won't be found, resulting in a linkage error.
 *
 * <h3>Value type conversions</h3>
 *
 * Automatic value type conversions are performed when setting variables or invoking methods:
 *
 * <ul>
 * <li>Widening — Example: {@code int} to {@code long}
 * <li>Boxing — Example: {@code int} to {@code Integer}
 * <li>Widening and boxing — Example: {@code int} to {@code Long}, {@code Number} or {@code Object}
 * <li>Reboxing and widening — Example: {@code Integer} to {@code Long}
 * <li>Unboxing — Example: {@code Integer} to {@code int} ({@code NullPointerException} is possible)
 * <li>Unboxing and widening — Example: {@code Integer} to {@code long} ({@code NullPointerException} is possible)
 * </ul>
 *
 * Automatic widening rules are stricter than what the Java language permits. In particular,
 * {@code int} cannot be automatically widened to {@code float}, and {@code long} cannot be
 * automatically widened to {@code double}. For these cases, an explicit cast is required.
 * In addition, a calculation on a small primitive type doesn't automatically get converted to
 * {@code int}:
 * {@snippet lang="java" :
 * // Make an unsigned conversion: int a = bytes[i] & 0xff;
 * // Without the cast, the 'and' operation only accepts a byte, and the result would be a byte.
 * var aVar = bytesVar.aget(iVar).cast(int.class).and(0xff);
 * }
 *
 * Narrowing of primitive constants is performed automatically if no information would be lost
 * in the conversion. If the above example didn't have an explicit cast, passing the {@code
 * 0xff} constant to an operation which accepts a {@code byte} would cause an exception to be
 * thrown at code generation time.  This is because bytes are signed, and {@code 0xff} is out
 * of bounds. A constant of {@code -1} would be accepted, although it wouldn't correctly
 * perform an unsigned conversion.
 *
 * <h2>Thread safety</h2>
 *
 * <p>The classes which implement the interfaces in this package aren't designed to be
 * thread-safe. Only one thread at a time should be interacting with a {@code ClassMaker}
 * instance and any other objects that affect its state.
 *
 * @see ClassMaker#begin()
 */
package org.cojen.maker;
