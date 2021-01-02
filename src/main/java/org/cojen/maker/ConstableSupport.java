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

import java.lang.constant.DynamicConstantDesc;

import java.lang.invoke.TypeDescriptor;

/**
 * Supports the java.lang.constant package, added in Java 12.
 *
 * @author Brian S O'Neill
 */
class ConstableSupport {
    static final ConstableSupport THE;

    static {
        ConstableSupport instance = null;
        try {
            if (TypeDescriptor.class != null) {
                instance = new ConstableSupport();
            }
        } catch (LinkageError e) {
        }

        if (instance == null) {
            instance = new Unsupported();
        }

        THE = instance;
    }

    /**
     * Returns null if not supported.
     */
    Type typeFrom(ClassLoader loader, Object type) {
        TypeDescriptor.OfField desc;
        if (type instanceof TypeDescriptor.OfField) {
            desc = (TypeDescriptor.OfField) type;
        } else if (type instanceof DynamicConstantDesc) {
            desc = ((DynamicConstantDesc) type).constantType();
        } else {
            return null;
        }
        return Type.from(loader, desc.descriptorString());
    }

    private static class Unsupported extends ConstableSupport {
        @Override
        Type typeFrom(ClassLoader loader, Object type) {
            return null;
        }
    }
}
