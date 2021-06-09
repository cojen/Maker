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

package org.cojen.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;

/**
 * Much smaller than the original Cojen example. https://github.com/cojen/Cojen/wiki/Example
 *
 * Based on HelloWorldBuilder from the Apache BCEL project.
 *
 * @author Brian S O'Neill
 */
public class HelloName {
    /* Generates this:

    public class HelloName {
        public static void main(String[] args) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String name;
            try {
                System.out.print("Please enter your name> ");
                name = in.readLine();
            } catch (IOException e) {
                return;
            }
            System.out.println("Hello, " + name);
        }
    }

    */

    public static void main(String[] args) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "main", String[].class).public_().static_();
        var sys = mm.var(System.class);
        var in = mm.new_(BufferedReader.class, mm.new_(InputStreamReader.class, sys.field("in")));
        Label tryStart = mm.label().here();
        sys.field("out").invoke("print", "Please enter your name> ");
        var name = in.invoke("readLine");
        mm.catch_(tryStart, IOException.class, ex -> mm.return_());
        sys.field("out").invoke("println", mm.concat("Hello, ", name));
        cm.finish().getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
