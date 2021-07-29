/*
 *  Copyright (C) 2019 Cojen.org
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

import java.lang.reflect.Modifier;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class Modifiers {
    // For classes, fields, and methods.
    static int toPublic(int bitmask) {
        return (bitmask | Modifier.PUBLIC) & (~Modifier.PROTECTED & ~Modifier.PRIVATE);
    }
    
    // For classes, fields, and methods.
    static int toPrivate(int bitmask) {
        return (bitmask | Modifier.PRIVATE) & (~Modifier.PUBLIC & ~Modifier.PROTECTED);
    }

    // For classes, fields, and methods.
    static int toProtected(int bitmask) {
        return (bitmask | Modifier.PROTECTED) & (~Modifier.PUBLIC & ~Modifier.PRIVATE);
    }
    
    // For classes, fields, and methods.
    static int toStatic(int bitmask) {
        return bitmask | Modifier.STATIC;
    }

    // For classes, fields, and methods.
    static int toFinal(int bitmask) {
        return (bitmask | Modifier.FINAL) & (~Modifier.INTERFACE & ~Modifier.ABSTRACT);
    }
    
    // For methods.
    static int toSynchronized(int bitmask) {
        return bitmask | Modifier.SYNCHRONIZED;
    }
    
    // For fields.
    static int toVolatile(int bitmask) {
        return bitmask | Modifier.VOLATILE;
    }
    
    // For fields.
    static int toTransient(int bitmask) {
        return bitmask | Modifier.TRANSIENT;
    }
    
    // For methods.
    static int toNative(int bitmask) {
        return (bitmask | Modifier.NATIVE) & ~Modifier.ABSTRACT;
    }
    
    // For classes.
    static int toInterface(int bitmask) {
        return (bitmask | (Modifier.INTERFACE | Modifier.ABSTRACT)) & ~Modifier.FINAL;
    }

    // For classes and methods.
    static int toAbstract(int bitmask) {
        return (bitmask | Modifier.ABSTRACT) &
            (~Modifier.FINAL & ~Modifier.NATIVE & ~Modifier.SYNCHRONIZED);
    }

    // For methods.
    static int toBridge(int bitmask) {
        // Bridge re-uses the Modifier.VOLATILE modifier, which used to only apply to fields.
        return bitmask | Modifier.VOLATILE;
    }

    // For classes and fields.
    static int toEnum(int bitmask) {
        return bitmask | 0x4000;
    }

    // For methods.
    static int toVarArgs(int bitmask) {
        // Varargs re-uses the Modifier.TRANSIENT modifier, which used to only apply to fields.
        return bitmask | Modifier.TRANSIENT;
    }

    // For classes, fields, and methods.
    static int toSynthetic(int bitmask) {
        return bitmask | 0x1000;
    }
}
