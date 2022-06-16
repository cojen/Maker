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

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an item which can have attributes.
 *
 * @author Brian S O'Neill
 */
abstract class Attributed {
    ConstantPool mConstants;

    List<Attribute> mAttributes;

    private Attribute.Annotations[] mAnnotationSets;

    Attributed(ConstantPool cp) {
        mConstants = cp;
    }

    void addAttribute(Attribute attr) {
        if (mAttributes == null) {
            mAttributes = new ArrayList<>(4);
        }
        mAttributes.add(attr);
    }

    public void addAttribute(String name, Object... values) {
        addAttribute(defineAttributes(name, values));
    }

    private Attribute defineAttribute(String name, Object value) {
        if (value == null) {
            return new Attribute.Empty(mConstants, name);
        } else if (value instanceof byte[]) {
            return new Attribute.Bytes(mConstants, name, (byte[]) value);
        } else if (!value.getClass().isArray()) {
            return new Attribute.Constant(mConstants, name, defineConstant(value));
        } else if (value instanceof Object[]) {
            return defineAttributes(name, (Object[]) value);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private Attribute defineAttributes(String name, Object... values) {
        if (values == null || values.length == 0) {
            return defineAttribute(name, null);
        } else if (values.length == 1) {
            return defineAttribute(name, values[0]);
        } else {
            var entries = new Attribute[values.length];
            for (int i=0; i<values.length; i++) {
                entries[i] = defineAttribute(null, values[i]);
            }
            return new Attribute.Composite(mConstants, name, entries);
        }
    }

    private ConstantPool.Constant defineConstant(Object value) {
        if (value instanceof String) {
            return mConstants.addUTF8((String) value);
        }

        if (value instanceof Class) {
            return mConstants.addClass(Type.from((Class) value));
        }

        if (value instanceof Typed) {
            return mConstants.addClass(((Typed) value).type());
        }

        if (value instanceof Number) {
            if (value instanceof Integer) {
                return mConstants.addInteger((int) value);
            }
            if (value instanceof Long) {
                return mConstants.addLong((long) value);
            }
            if (value instanceof Float) {
                return mConstants.addFloat((float) value);
            }
            if (value instanceof Double) {
                return mConstants.addDouble((double) value);
            }
        }

        throw new IllegalArgumentException();
    }

    TheAnnotationMaker addAnnotation(TheAnnotationMaker am, boolean visible) {
        if (mAnnotationSets == null) {
            mAnnotationSets = new Attribute.Annotations[2];
        }
        int which = visible ? 0 : 1;
        Attribute.Annotations annotations = mAnnotationSets[which];
        if (annotations == null) {
            annotations = new Attribute.Annotations(mConstants, visible);
            addAttribute(annotations);
            mAnnotationSets[which] = annotations;
        }
        annotations.add(am);
        return am;
    }

    void writeAttributesTo(BytesOut out) throws IOException {
        if (mAttributes == null) {
            out.writeShort(0);
        } else {
            TheClassMaker.checkSize(mAttributes, 65535, "Attribute");
            out.writeShort(mAttributes.size());
            for (Attribute attr : mAttributes) {
                attr.writeTo(out);
            }
        }
    }
}
