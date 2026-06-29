/*
 *  Copyright 2022 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

/**
 * Base interface for making classes, methods, and fields.
 *
 * @author Brian S O'Neill
 */
public interface Maker {
    /**
     * Returns the name of the item being made.
     */
    String name();

    /**
     * Switch this item to be public.
     *
     * @return this
     */
    Maker public_();

    /**
     * Switch this item to be private.
     *
     * @return this
     */
    Maker private_();

    /**
     * Switch this item to be protected.
     *
     * @return this
     */
    Maker protected_();

    /**
     * Switch this item to be static.
     *
     * @return this
     */
    Maker static_();

    /**
     * Switch this item to be final.
     *
     * @return this
     */
    Maker final_();

    /**
     * Indicate that this item is synthetic.
     *
     * @return this
     */
    Maker synthetic();

    /**
     * Define a signature for this member, which is a string for supporting generic types.
     * The components can be strings or types (class, ClassMaker, etc.), which are concatenated
     * into a single string. Consult the JVM specification for the signature syntax.
     *
     * @throws IllegalArgumentException if given an unsupported component
     * @return this
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Maker signature(Object... components);

    /**
     * Returns the {@code ClassMaker} for this item, which can also be used as a type
     * specification.
     */
    ClassMaker classMaker();

    /**
     * Add an annotation to this item.
     *
     * @param annotationType name or class which refers to an annotation interface
     * @param visible true if annotation is visible at runtime
     * @throws IllegalArgumentException if the annotation type is unsupported
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    AnnotationMaker addAnnotation(Object annotationType, boolean visible);

    /**
     * Add a generic JVM attribute which optionally references a constant value. This is an
     * advanced feature for defining attributes which aren't directly supported by the core
     * maker API. Allowed value types are: int, float, long, double, String, Class, raw byte[],
     * or an array of values. Arrays aren't encoded with any length prefix, but a raw byte[] as
     * the first element can be interpreted as such.
     */
    void addAttribute(String name, Object value);

    /**
     * Mangles a name as described by <a href="https://web.archive.org/web/20160622140347/http://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm">symbolic freedom in the VM</a>.
     * If the name doesn't need to be mangled, then the original name is returned. Otherwise,
     * the returned mangled name starts with a backslash character.
     *
     * @see #demangle
     */
    public static String mangle(String name) {
        int length = name.length();

        if (length == 0) {
            return "\\=";
        }

        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            char m = mangle(name, i, c);

            if (m != '\0') {
                var b = new StringBuilder(name.length() + 3);
                if (i > 0 && name.charAt(0) != '\\') {
                    b.append("\\=");
                }
                b.append(name, 0, i).append('\\').append(m);
                i++;

                for (; i<name.length(); i++) {
                    c = name.charAt(i);
                    m = mangle(name, i, c);
                    if (m == '\0') {
                        b.append(c);
                    } else {
                        b.append('\\').append(m);
                    }
                }

                return b.toString();
            }
        }

        return name;
    }

    /**
     * @return 0 if no need to mangle
     */
    private static char mangle(String name, int i, char c) {
        return switch (c) {
            default  -> '\0';

            case '/' -> '|';
            case '.' -> ',';
            case ';' -> '?';
            case '$' -> '%';
            case '<' -> '^';
            case '>' -> '_';
            case '[' -> '{';
            case ']' -> '}';
            case ':' -> '!';

            case '\\' -> {
                if (++i < name.length()) {
                    // Only need to escape if it would be interpeted as an accidental escape.
                    yield switch (name.charAt(i)) {
                        default  -> '\0';
                        case '|', ',', '?', '%', '^', '_', '{', '}', '!', '-' -> '-';
                        case '=' -> i <= 1 ? '-' : '\0';
                    };
                }
                yield '\0';
            }
        };
    }

    /**
     * Demangles a name. If the given string doesn't start with a backslash, then the original
     * string is returned. If the string starts with {@code \=} (backslash, equals), then that
     * portion is always trimmed off, even if the rest of the string isn't mangled.
     *
     * @see #mangle
     */
    public static String demangle(String mangled) {
        int length = mangled.length();

        if (length > 0) {
            char c = mangled.charAt(0);

            if (c == '\\' && length > 1) {
                var b = new StringBuilder(length - 1);
                int i = 1 + demangle(b, 1, mangled.charAt(1));

                while (i < length) {
                    c = mangled.charAt(i++);
                    if (c != '\\' || i >= length) {
                        b.append(c);
                    } else {
                        i += demangle(b, i, mangled.charAt(i));
                    }
                }

                return b.toString();
            }
        }

        return mangled;
    }

    private static int demangle(StringBuilder b, int i, char c) {
        switch (c) {
            default  -> {
                // Wasn't a real escape.
                b.append('\\');
                return 0;
            }

            case '|' -> c = '/';
            case ',' -> c = '.';
            case '?' -> c = ';';
            case '%' -> c = '$';
            case '^' -> c = '<';
            case '_' -> c = '>';
            case '{' -> c = '[';
            case '}' -> c = ']';
            case '!' -> c = ':';
            case '-' -> c = '\\';

            case '=' -> {
                if (i <= 1) {
                    return 1;
                }
                // "\=" is only treated as an escape at the start of the string.
                b.append('\\');
            }
        }

        b.append(c);

        return 1;
    }
}
