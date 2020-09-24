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
 * <pre>
 * ClassMaker cm = ClassMaker.begin().public_();
 *
 * // public static void run()...
 * MethodMaker mm = cm.addMethod(null, "run").public_().static_();
 *
 * // System.out.println(...
 * mm.var(System.class).field("out").invoke("println", "hello, world");
 *
 * Class&lt;?&gt; clazz = cm.finish();
 * clazz.getMethod("run").invoke(null);
 * </pre>
 *
 * <p>The classes which implement the interfaces in this package aren't designed to be
 * thread-safe. Only one thread at a time should be interacting with a {@code ClassMaker}
 * instance and any of the objects produced by it.
 *
 * @see ClassMaker
 */
package org.cojen.maker;
