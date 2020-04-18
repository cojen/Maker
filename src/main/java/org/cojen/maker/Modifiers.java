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
    static int toPublic(int bitmask) {
        return (bitmask | Modifier.PUBLIC) & (~Modifier.PROTECTED & ~Modifier.PRIVATE);
    }
    
    static int toPrivate(int bitmask) {
        return (bitmask | Modifier.PRIVATE) & (~Modifier.PUBLIC & ~Modifier.PROTECTED);
    }

    static int toProtected(int bitmask) {
        return (bitmask | Modifier.PROTECTED) & (~Modifier.PUBLIC & ~Modifier.PRIVATE);
    }
    
    static int toStatic(int bitmask) {
        return bitmask | Modifier.STATIC;
    }

    static int toFinal(int bitmask) {
        return (bitmask | Modifier.FINAL) & (~Modifier.INTERFACE & ~Modifier.ABSTRACT);
    }
    
    static int toSynchronized(int bitmask) {
        return (bitmask | Modifier.SYNCHRONIZED) &
            (~Modifier.VOLATILE & ~Modifier.TRANSIENT & ~Modifier.INTERFACE);
    }
    
    static int toVolatile(int bitmask) {
        return (bitmask | Modifier.VOLATILE) &
            (~Modifier.SYNCHRONIZED & ~Modifier.NATIVE & ~Modifier.INTERFACE &
             ~Modifier.ABSTRACT & ~Modifier.STRICT);
    }
    
    static int toTransient(int bitmask) {
        return (bitmask | Modifier.TRANSIENT) &
            (~Modifier.SYNCHRONIZED & ~Modifier.NATIVE &
             ~Modifier.INTERFACE & ~Modifier.ABSTRACT & ~Modifier.STRICT);
    }
    
    static int toNative(int bitmask) {
        return (bitmask | Modifier.NATIVE) & 
            (~Modifier.VOLATILE & ~Modifier.TRANSIENT &
             ~Modifier.INTERFACE & ~Modifier.ABSTRACT & ~Modifier.STRICT);
    }
    
    static int toInterface(int bitmask) {
        return (bitmask | (Modifier.INTERFACE | Modifier.ABSTRACT)) & 
            (~Modifier.FINAL & ~Modifier.SYNCHRONIZED &
             ~Modifier.VOLATILE & ~Modifier.TRANSIENT & ~Modifier.NATIVE);
    }

    static int toAbstract(int bitmask) {
        return (bitmask | Modifier.ABSTRACT) & 
            (~Modifier.FINAL & ~Modifier.VOLATILE & ~Modifier.TRANSIENT & ~Modifier.NATIVE &
             ~Modifier.SYNCHRONIZED & ~Modifier.STRICT);
    }

    static int toStrict(int bitmask) {
        return bitmask | Modifier.STRICT;
    }

    static int toBridge(int bitmask) {
        // Bridge re-uses the Modifier.VOLATILE modifier, which used to only apply to fields.
        return (bitmask | Modifier.VOLATILE) &
            (~Modifier.NATIVE & ~Modifier.INTERFACE & ~Modifier.ABSTRACT);
    }

    static int toEnum(int bitmask) {
        // Enum re-uses the Modifier.NATIVE modifier, which used to only apply to methods.
        return (bitmask | Modifier.NATIVE) &
            (~Modifier.ABSTRACT & ~Modifier.INTERFACE &
             ~Modifier.STRICT & ~Modifier.SYNCHRONIZED);
    }

    static int toVarArgs(int bitmask) {
        // Enum re-uses the Modifier.TRANSIENT modifier, which used to only apply to fields.
        return (bitmask | Modifier.TRANSIENT) & (~Modifier.INTERFACE & ~Modifier.VOLATILE);
    }
}
