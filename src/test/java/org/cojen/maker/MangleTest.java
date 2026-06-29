/*
 *  Copyright 2026 Cojen.org
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

import java.util.Random;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class MangleTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(MangleTest.class.getName());
    }

    @Test
    public void basicMangle() {
        mangleTest("", "\\=");
        mangleTest("hello", "hello");
        mangleTest("hello\\", "hello\\");
        mangleTest("hello\\=", "hello\\=");
        mangleTest("\\", "\\");
        mangleTest("\\x", "\\x");
        mangleTest("\\\\x", "\\\\x");
        mangleTest("\\=foo", "\\-=foo");
        mangleTest("\\-bar", "\\--bar");
        mangleTest("\\--bar", "\\---bar");
        mangleTest("baz\\!", "\\=baz\\-!");
        mangleTest("]\\", "\\}\\");
    }

    private static void mangleTest(String name, String mangled) {
        String m = Maker.mangle(name);
        assertEquals(mangled, m);
        assertEquals(name, Maker.demangle(m));
    }

    @Test
    public void fuzzMangle() {
        var rnd = new Random(8675309);

        for (int i=0; i<1000; i++) {
            int len = rnd.nextInt(10) + 1;
            var b = new StringBuilder(len);

            for (int j=0; j<len; j++) {
                if (rnd.nextInt(5) == 0) {
                    b.append(switch (rnd.nextInt(10)) {
                        default -> '/';
                        case 1 -> '.';
                        case 2 -> ';';
                        case 3 -> '$';
                        case 4 -> '<';
                        case 5 -> '>';
                        case 6 -> '[';
                        case 7 -> ']';
                        case 8 -> ':';
                        case 9 -> '\\';
                    });
                } else {
                    b.append((char) (rnd.nextInt(26) + 'a'));
                }
            }

            var name = b.toString();

            String m = Maker.mangle(name);
            String d = Maker.demangle(m);

            assertEquals(name, d);

            m = Maker.mangle(m);
            d = Maker.demangle(Maker.demangle(m));

            assertEquals(name, d);
        }
    }

    @Test
    public void basicDemangle() {
        demangleTest("", "");
        demangleTest("\\", "\\");
        demangleTest("\\=", "");
        demangleTest("hello\\", "hello\\");
        demangleTest("hello\\=", "hello\\=");
        demangleTest("\\=he\\=llo", "he\\=llo");
        demangleTest("hello\\_", "hello\\_");
    }

    private static void demangleTest(String mangled, String demangled) {
        assertEquals(demangled, Maker.demangle(mangled));
    }
}
