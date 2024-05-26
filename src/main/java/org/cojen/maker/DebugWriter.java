/*
 *  Copyright 2021 Cojen.org
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

package org.cojen.maker;

import java.io.File;
import java.io.FileOutputStream;

import java.lang.reflect.Modifier;

import java.util.Set;

/**
 * Only used when TheClassMaker.DEBUG is true.
 *
 * @author Brian S O'Neill
 */
class DebugWriter {
    private static int counter;

    private static synchronized int next() {
        return counter++;
    }

    static void write(TheClassMaker cm, byte[] bytes) {
        String className = cm.name();
        File file = new File("ClassMaker/" + className + '(' + next() + ").class");
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            file = new File(tempDir, file.getPath());
            file.getParentFile().mkdirs();

            var msg = new StringBuilder("ClassMaker writing to ").append(file);

            if (Modifier.isAbstract(cm.mModifiers)) {
                Set<String> unimplemented = cm.unimplementedMethods();
                if (!unimplemented.isEmpty()) {
                    msg.append("; unimplemented methods: ").append(unimplemented);
                }
            }

            System.out.println(msg);

            try (var out = new FileOutputStream(file)) {
                out.write(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
}
